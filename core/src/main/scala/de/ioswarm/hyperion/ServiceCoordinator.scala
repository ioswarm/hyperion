package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorPath, ActorRef}
import akka.http.scaladsl.server.Route

/**
  * Created by andreas on 05.11.17.
  */
private[hyperion] final class ServiceCoordinator(service: Service[NotUsed]) extends ServiceActor(service) {

  import ServiceCommands._

  private def flat(s: Service[_], path: String): Seq[(ActorPath, Service[_])] = (ActorPath.fromString(path+"/"+s.name), s) +: s.children.flatMap(sx => flat(sx, path+"/"+s.name))
  var serviceIndex: Map[ActorPath, Service[_]] = flat(service, self.path.address+"/user").drop(1).toMap
  var serviceRefs: Set[(Service[_], ActorRef)] = Set.empty[(Service[_], ActorRef)]

  private def serviceDiff() = serviceIndex.keySet.diff(serviceRefs.map(r => r._2.path))

  override def preStart(): Unit = {
    super.preStart()
    if (service.children.isEmpty) context.become(runtimeReceive)
    else service.children.foreach(child => serviceContext.actorOf(child))
  }

  def startupReceive: Receive = {
    case Register(ref) =>
      serviceRefs += serviceIndex(ref.path) -> ref
      log.info(ref.path+" registered")
      val diff = serviceDiff()
      if (serviceDiff().isEmpty) {
        log.info("All services registered ... starting subsequent services")
        context.become(subsequentReceive)
        self ! StartSubsequentServices
      } else log.info("Wait for {}", diff.mkString(", "))
  }

  protected def createHttpService(hostname: String, port: Int, services: Set[(DefaultService[Any], ActorRef)]): Unit = {
    if (services.nonEmpty) {
      import akka.http.scaladsl.server.Directives._

      val first: Route = services.head._1.route(services.head._2)
      val route: Route = services.drop(1).aggregate(first)({(route,ref) => route ~ ref._1.route(ref._2)}, {(r1,r2) => r1 ~ r2})

      val service = HttpService(hostname, port, route)
      val ref = serviceContext.actorOf(service)
      serviceIndex += ref.path -> service
      serviceRefs += service -> ref
    }
  }

  def subsequentReceive: Receive = {
    case StartSubsequentServices =>
      createHttpService("localhost", 9000, serviceRefs.filter(_._1.isInstanceOf[DefaultService[_]]).map(t => (t._1.asInstanceOf[DefaultService[Any]], t._2)) .filter(ref => ref._1.hasRoute))

      val reaper = context.actorOf(ServiceReaper.props(self), "reaper")
      serviceRefs.foreach(t => reaper ! WatchService(t._2))
      log.info("Subsequent services started ... switch to runtime-mode...")
      context.become(runtimeReceive)

  }

  override def runtimeReceive: Receive = {
    case Stop =>
      serviceRefs.foreach(t => t._2 ! Stop)
  }

  override def receive: Receive = startupReceive

  override def serviceReceive: Receive = Actor.emptyBehavior
}