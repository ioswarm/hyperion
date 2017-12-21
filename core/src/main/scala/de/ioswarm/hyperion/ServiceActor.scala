package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, PoisonPill, Terminated}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.typesafe.config.Config

/**
  * Created by andreas on 31.10.17.
  */
object ServiceActor {

  case object InitializeService
  final case class ServiceInitialized(ref: ActorRef)

  case object StopService
  final case class ServiceStopped(ref: ActorRef)

  final case class WatchService(ref: ActorRef)

}
abstract class ServiceActor[T](service: Service[T]) extends Actor with ActorLogging {

  import ServiceActor._

  implicit val serviceContext: Context = new Context(context, log)

  def serviceReceive: Receive

  //val childServiceIndex: Seq[(ActorPath, Service[_])] = service.children.map(s => (ActorPath.fromString(s"${self.path}/${s.name}"), s))
  val childPaths: Seq[ActorPath] = service.children.map(s => ActorPath.fromString(s"${self.path}/${s.name}"))
  def childServiceDiff: Seq[ActorPath] = childPaths.diff(context.children.toList.map(_.path))


  def config: Config = context.system.settings.config.getConfig("hyperion")


  def initializeReceive: Receive = {
    case InitializeService =>
      if (service.children.isEmpty) {
        context.parent ! ServiceInitialized(self)
        context.become(runtimeReceive)
      } else service.children.foreach(cs => serviceContext.actorOf(cs) ! InitializeService)

    case ServiceInitialized(ref) =>
      context.parent ! WatchService(ref)
      if (childServiceDiff.isEmpty) {
        context.parent ! ServiceInitialized(self)
        context.become(runtimeReceive)
      }

    case ws: WatchService =>
      context.parent ! ws
  }

  def runtimeActorReceive: Receive = {
    case StopService =>
      log.debug("Receive 'StopService' event ...")
      if (context.children.isEmpty){
        sender() ! ServiceStopped(self)
        context.stop(self)
      } else {
        context.become(stopReceive)
        context.children.foreach{ref =>
          if (childPaths.contains(ref.path)) ref ! StopService
          else {
            context.watch(ref)
            ref ! PoisonPill
          }
        }
      }
    case ws: WatchService =>
      context.parent ! ws
  }

  def runtimeReceive: Receive = serviceReceive orElse runtimeActorReceive

  def stopReceive: Receive = {
    case ServiceStopped(_) =>
      if (context.children.isEmpty)
        context.parent ! ServiceStopped(self)
    case Terminated(ref) =>
      context.unwatch(ref)
      if (context.children.isEmpty)
        context.parent ! ServiceStopped(self)
  }

  override def receive: Receive = initializeReceive
}

private[hyperion] class DefaultServiceActor(service: DefaultService[NotUsed]) extends ServiceActor(service) {
  override def serviceReceive: Receive = service.receive(serviceContext)
}

private[hyperion] class EventServiceProxy[T](service: EventService[T]) extends ServiceActor[T](service) {

  import ServiceActor._

  val shardRef: ActorRef = ClusterSharding(context.system).startProxy(
    typeName = service.shardName
    , role = None
    , extractEntityId = service.idExtractor
    , extractShardId = service.shardResolver
  )

  override def serviceReceive: Receive = {
    case a: Any => shardRef forward a
  }

  override def initializeReceive: Receive = {
    case InitializeService =>
      context.parent ! ServiceInitialized(self)
      context.become(runtimeReceive)
  }

  override def runtimeActorReceive: Receive = {
    case StopService =>
      log.debug("Receive 'StopService' event ...")
      context.parent ! ServiceStopped(self)
  }

}

private[hyperion] class EventServiceActor[T](service: EventService[T]) extends ServiceActor[T](service) {

  import ServiceActor._

  val shardRef: ActorRef = ClusterSharding(context.system).start(
    typeName = service.shardName
    , entityProps = service.persistentProps
    , settings = ClusterShardingSettings(context.system)
    , extractEntityId = service.idExtractor
    , extractShardId = service.shardResolver
  )


  override def serviceReceive: Receive = {
    case a: Any => shardRef forward a
  }

  override def initializeReceive: Receive = {
    case InitializeService =>
      context.watch(shardRef)
      context.parent ! WatchService(shardRef)
      context.parent ! ServiceInitialized(self)
      context.become(runtimeReceive)
  }

  override def runtimeActorReceive: Receive = {
    case StopService =>
      log.debug("Receive 'StopService' event ...")
      shardRef ! ShardRegion.GracefulShutdown
      context.become(stopReceive)
  }

  override def stopReceive: Receive = {
    case Terminated(ref) =>
      log.debug("ShardService {} stopped.", service.shardName)
      context.unwatch(ref)
      context.parent ! ServiceStopped(self)
  }

}

object PersistentServiceActor {
  case object Stop
}
private[hyperion] class PersistentServiceActor[T](service: EventService[T]) extends PersistentActor with ActorLogging {

  import akka.actor.ReceiveTimeout
  import PersistentServiceActor._

  val snapshotInterval: Int = service.snapshotInterval
  var value: Option[T] = service.value

  context.setReceiveTimeout(service.receiveTimeout)

  override def persistenceId: String = self.path.name

  def receiveEvent(value: Option[T], evt: Event): Option[T] = {
    val x = service.eventReceive(value)(evt)
    log.debug("Receive-Event {} - old-value: {} - new-value: {}", evt, value, x)
    Some(x)
  }

  override def receiveCommand: Receive = {
    case cmd: Command =>
      val action =  service.commandReceive(value)(cmd)
      if (action.isPersistable) {
        persist(action.taggedValue) { evt =>
          log.debug("TAGGED: "+evt)
          value = receiveEvent(value, evt.payload.asInstanceOf[Event])
          if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0 && value.isDefined)
            saveSnapshot(value.get)
          if (action.isReplyable) sender() ! evt.payload.asInstanceOf[Event]
        }
      } else if (action.isReplyable) sender() ! action.value
    case ReceiveTimeout =>
      log.debug("Receive timeout ... passivate persistenceId: "+persistenceId)
      context.parent ! Passivate(stopMessage = Stop)
    case Stop =>
      log.debug("Stopping persistenceId: "+persistenceId)
      context.stop(self)
  }

  override def receiveRecover: Receive = {
    case evt: Event =>
      value = receiveEvent(value, evt)
    case SnapshotOffer(_, snapshot: Any) =>
      log.debug("Recover Snapshot: {}", snapshot)
      value = Some(snapshot.asInstanceOf[T])
  }

}
