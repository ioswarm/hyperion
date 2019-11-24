package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import de.ioswarm.hyperion.http.RouteAppender

private[hyperion] class HyperionClusterActor(settings: Hyperion.Settings) extends Actor with ActorLogging {

  import Hyperion._

  val cluster = Cluster(context.system)

  val routeAppender: ActorRef = context.actorOf(Props(
    classOf[RouteAppender]
    , settings.httpHost
    , settings.httpPort
    , Class.forName(settings.httpActorClassName)
  ), "routes")

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive: Receive = {
    case HttpAppendRoute(route) => routeAppender ! RouteAppender.AppendRoute(route)

    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)

    case _ =>
  }

}