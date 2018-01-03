package de.ioswarm.hyperion

import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.ioswarm.hyperion.Hyperion.{Initialize, Initialized}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Management {

  def apply(config: Config): ActorService = {
    import scala.collection.JavaConverters._

    ActorServiceImpl(
      "management"
      , actorClass = classOf[Management]
      , children = config.getStringList("management.extensions").asScala.map(cn => Class.forName(cn).newInstance().asInstanceOf[ManagerService])
    )
  }

}
class Management(service: ActorService) extends ActorServiceActor(service) {

  import context.dispatcher

  override def config: Config = super.config.getConfig("hyperion.management")

  def managementRoute: Route = {
    import akka.http.scaladsl.server.Directives._

    val services = childServiceMap.filter(t => t._2.isInstanceOf[ManagerService]).map(e => (e._1, e._2.asInstanceOf[ManagerService]))
    val first: Route = services.head._2.route(services.head._1)
    services.drop(1).aggregate(first)({(route,srv) => route ~ srv._2.route(srv._1)}, {(r1, r2) => r1 ~ r2})
  }

  override def serviceReceive: Receive = {
    case Initialize =>
      val repl = context.sender()
      log.debug("Initialize service {} at {}", service.name, self.path)
      Future sequence service.children.map(c => startChildService(c)) onComplete {
        case Success(_) =>
          val host = config.getString("hostname")
          val port = config.getInt("port")
          startChildService(RouteService(host, port, managementRoute)) onComplete {
            case Success(_) =>
              log.debug("Service {} at {} initialized.", service.name, self.path)
              register(self)
              repl ! Initialized(self)
            case Failure(e) =>
              log.error(e, "Could not start management-route-service listening on {}:{}", host, port)
              repl ! akka.actor.Status.Failure(e)
          }
        case Failure(e) =>
          log.error(e, "Error while starting child-services of {} at {}", service.name, self.path)
          repl ! akka.actor.Status.Failure(e)
      }
  }

  override def receive: Receive = serviceReceive orElse actorReceive

}
