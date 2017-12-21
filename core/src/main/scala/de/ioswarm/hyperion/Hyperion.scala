package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorPath, ActorRef, ActorSystem, Terminated}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.ServiceActor.{InitializeService, StopService}

import scala.concurrent.Future

object Hyperion {
  def apply(implicit system: ActorSystem): Hyperion = new HyperionImpl(system)
  def apply(name: String): Hyperion = new HyperionImpl(ActorSystem(name))

  private[hyperion] class HyperionServiceActor(service: Service[NotUsed]) extends ServiceActor[NotUsed](service) {

    import de.ioswarm.hyperion.ServiceActor._
    import collection.mutable

    private def flat(s: Service[_], path: String): Seq[(ActorPath, Service[_])] = (ActorPath.fromString(path+"/"+s.name), s) +: s.children.flatMap(sx => flat(sx, path+"/"+s.name))
    var serviceIndex: Map[ActorPath, Service[_]] = flat(service, self.path.address+"/user").drop(1).toMap
    var serviceRefs: Set[(Service[_], ActorRef)] = Set.empty[(Service[_], ActorRef)]

    val refs: mutable.ArrayBuffer[ActorRef] = mutable.ArrayBuffer.empty[ActorRef]


    private def serviceDiff: Set[ActorPath] = serviceIndex.keySet.diff(context.children.map(_.path).toSet)

    def watch(ref: ActorRef): Unit = {
      log.debug("watch {}", ref.path)
      context.watch(ref)
      refs += ref
    }

    def unwatch(ref: ActorRef): Unit = {
      log.debug("unwatch {}", ref.path)
      refs -= ref
      if (refs.isEmpty) context.system.terminate()
    }

    def stop(): Unit = {
      log.info("receive system-shutdown-event from {}", context.sender().path)
      context.children.foreach(_ ! StopService)
    }

    private def registerLocal(service: Service[_]): ActorRef = {
      log.debug("Register service '{}' local.", service.name)
      val ref = serviceContext.actorOf(service)
      serviceIndex += ref.path -> service
      serviceRefs += service -> ref
      ref
    }

    private def serviceRoute(services: Set[(DefaultService[Any], ActorRef)]): Route = {
      import akka.http.scaladsl.server.Directives._

      val first: Route = services.head._1.route(services.head._2)
      services.drop(1).aggregate(first)({(route,ref) => route ~ ref._1.route(ref._2)}, {(r1,r2) => r1 ~ r2})
    }

    override def initializeReceive: Receive = {
      case InitializeService =>
        if (service.children.isEmpty) {
          // TODO ???
          context.become(runtimeReceive)
        } else service.children.foreach(cs => serviceContext.actorOf(cs) ! InitializeService)

      case ServiceInitialized(ref) =>
        serviceRefs += serviceIndex(ref.path) -> ref
        watch(ref)
        log.info(ref.path+" started")
        val diff = serviceDiff
        if (diff.isEmpty) {
          log.debug("All services registered ... starting subsequent services")
          val serviceRoutes = serviceRefs
            .filter(_._1.isInstanceOf[DefaultService[_]])
            .map(ref => (ref._1.asInstanceOf[DefaultService[Any]], ref._2))
            .filter(_._1.hasRoute)

          // HttpService
          if (serviceRoutes.nonEmpty) {
            val host = config.getString("http.hostname")
            val port = config.getInt("http.port")
            log.debug("Starting HttpService at {}:{}", host, port)
            registerLocal(HttpService(host, port, serviceRoute(serviceRoutes)))
          }

          if (config.getBoolean("management.enabled")) {
            val host = config.getString("management.hostname")
            val port = config.getInt("management.port")
            log.debug("Starting ManagementService at {}:{}", host, port)
            // TODO implement endpoints
          }

          context.become(runtimeReceive)
        } else log.debug("Wait for {}", diff.mkString(", "))
      case WatchService(ref) =>
        watch(ref)
      case Terminated(ref) =>
        unwatch(ref)
      case StopService =>
        stop()
    }

    override def runtimeReceive: Receive = {
      case WatchService(ref) =>
        watch(ref)
      case Terminated(ref) =>
        unwatch(ref)
      case StopService =>
        stop()
    }

    override def receive: Receive = initializeReceive

    override def serviceReceive: Receive = Actor.emptyBehavior

  }

}
trait Hyperion {

  def name: String = system.name
  def system: ActorSystem
  def run(services: Service[_]*): ActorRef
  def stop(): Unit

  def terminate(): Future[Terminated] = {
    stop()
    system.terminate()
  }

  def whenTerminated: Future[Terminated] = system.whenTerminated

}
private[hyperion] class HyperionImpl(val system: ActorSystem) extends Hyperion {

  import Hyperion._

  private var hyperionActor: Option[ActorRef] = None

  private def hyperionService(services: Seq[Service[_]]): Service[NotUsed] = Service(
    name = "hyperion"
    , actorClass = classOf[HyperionServiceActor]
    , children = services
  )

  override def run(services: Service[_]*): ActorRef = {
    if (hyperionActor.isEmpty) {
      val service = hyperionService(services)
      val ref = system.actorOf(service.props, service.name)
      ref ! InitializeService
      hyperionActor = Some(ref)
    }
    hyperionActor.get
  }

  override def stop(): Unit = {
    hyperionActor.foreach(_ ! StopService)
    hyperionActor = None
  }

}
