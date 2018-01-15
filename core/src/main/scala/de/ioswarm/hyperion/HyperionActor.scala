package de.ioswarm.hyperion

import akka.Done
import akka.actor.{ActorPath, ActorRef, Terminated}
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config

private[hyperion] class HyperionActor(service: ActorService) extends ActorServiceActor(service) {

  import context.dispatcher
  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.Future
  import scala.concurrent.duration._
  import scala.collection.mutable
  import scala.util.{Failure, Success}
  import Hyperion._

  val watchedActors: mutable.ArrayBuffer[ActorRef] = mutable.ArrayBuffer.empty[ActorRef]

  def children(s: Service): Seq[Service] = s match {
    case as: ActorService => as.children
    case ht: HttpService => ht.service match {
      case as: ActorService => as.children
      case _ => Seq.empty[Service]
    }
    case _ => Seq.empty[Service]
  }
  def flat(s: Service, path: String): Seq[(Service, ActorPath)] = (s, ActorPath.fromString(path+"/"+s.name)) +: children(s).flatMap(sx => flat(sx, path+"/"+s.name))

//  val serviceIndex: Map[Service, ActorPath] = flat(service, self.path.address+"/user").drop(1).toMap

  lazy val httpServiceList: Seq[Future[(HttpService, ActorRef)]] = flat(service, self.path.address + "/user").drop(1).filter(t => t._1.isInstanceOf[HttpService]).map(t => context.system.actorSelection(t._2).resolveOne(200.millis).map(ref => (t._1.asInstanceOf[HttpService], ref)))
  def serviceRoute: Future[Route] = {
    Future.sequence(httpServiceList).map { services =>
      import akka.http.scaladsl.server.Directives._

      val first: Route = services.head._1.route(services.head._2)
      services.drop(1).aggregate(first)({ (route, srv) => route ~ srv._1.route(srv._2) }, { (r1, r2) => r1 ~ r2 })
    }
  }

  /*def httpServiceMap: mutable.Map[ActorRef, HttpService] = childServiceMap.filter(t => t._2.isInstanceOf[HttpService] && t._2.asInstanceOf[HttpService].hasRoute).map(e => (e._1, e._2.asInstanceOf[HttpService]))
  def serviceRoute: Route = {
    import akka.http.scaladsl.server.Directives._

    val services = httpServiceMap.toList
    val first: Route = services.head._2.route(services.head._1)
    services.drop(1).aggregate(first)({(route,srv) => route ~ srv._2.route(srv._1)}, {(r1, r2) => r1 ~ r2})
  }*/

  override def config: Config = super.config.getConfig("hyperion")

  override def canRegister: Boolean = false

  override def serviceReceive: Receive = {
    case Initialize =>
      val repl = context.sender()
      log.debug("Initialize {}", service.name)
      Future sequence service.children.map(c => startChildService(c)) onComplete {
        case Success(_) =>
          // start Route-Service
          if (httpServiceList.nonEmpty) {
            val host = config.getString("http.hostname")
            val port = config.getInt("http.port")
            serviceRoute.flatMap(route => startChildService(RouteService(host, port, route))) onComplete {
              case Success(_) =>
                log.debug("Hyperion initialized.")
                repl ! Initialized(self)
              case Failure(e) =>
                log.error(e, "Could not start route-service listening on {}:{}", host, port)
                repl ! akka.actor.Status.Failure(e)
            }
          } else {
            log.debug("Hyperion initialized.")
            repl ! Initialized(self)
          }
        case Failure(e) =>
          log.error(e, "Error while starting child-services of {}", service.name)
          repl ! akka.actor.Status.Failure(e)
      }

    // TODO implement service-reaper-lifecycle for Shutdown and Termination !?!
    case Shutdown =>
      val repl = sender()
      implicit val timeout: Timeout = Timeout(10.seconds)  // TODO configure shutdown-timeout
      self ? Stop onComplete {
        case Success(_) =>
          repl ! Done
          context.system.terminate()
        case Failure(e) =>
          log.error(e, "Could not shutdown hyperion.")
          repl ! akka.actor.Status.Failure(e)
      }

    case Register(ref) =>
      log.debug("Watch actor {} as service-reaper.", ref.path)
      context.watch(ref)
      watchedActors += ref

    case Terminated(ref) =>
      log.debug("Remove actor {} from service-reaper list.", ref.path)
      watchedActors -= ref

  }

  override def receive: Receive = serviceReceive orElse actorReceive

}
