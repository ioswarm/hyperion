package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ReceiveTimeout, SupervisorStrategy}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import de.ioswarm.hyperion.model.{Command, Event}

abstract class ServiceActor(service: Service) extends Actor with ActorLogging {

  val serviceContext = new ServiceContext(context, log)

  context.setReceiveTimeout(service.options.timeout)

  override def supervisorStrategy: SupervisorStrategy = service.options.supervisorStrategy

  override def preStart(): Unit = service.options.preStart(serviceContext)

  override def postStop(): Unit = service.options.postStop(serviceContext)

  def onTimeout(): Unit = service.options.onTimeout(serviceContext)

  def serviceReceive: Service.ServiceReceive

  def receive: Receive = serviceReceive(serviceContext)

  def receiveTimeout: Receive = {
    case ReceiveTimeout =>
      log.debug("Receive timeout.")
      onTimeout()
  }

}

final class ReceivableServiceActor(service: ReceivableService) extends ServiceActor(service) {

  override def serviceReceive: Service.ServiceReceive = service.receive

}

final class PersistentServiceActor[T](service: PersistentService[T]) extends PersistentActor with ActorLogging {
  val serviceContext = new ServiceContext(context, log)
  val snapshotInterval: Int = service.snapshotInterval
  var data: T = service.value

  context.setReceiveTimeout(service.options.timeout)

  override def preStart(): Unit = service.options.preStart(serviceContext)

  override def postStop(): Unit = service.options.postStop(serviceContext)

  def onTimeout(): Unit = service.options.onTimeout(serviceContext)

  override def persistenceId: String = self.path.name

  override def receiveRecover: Receive = {
    case evt: Event =>
      data = service.eventReceive(serviceContext)(data)(evt)
    case SnapshotOffer(_, snapshot: Any) =>
      log.debug("Recover snapshot for persistence-id {} with data: {}", persistenceId, snapshot)
      data = snapshot.asInstanceOf[T]
    case RecoveryCompleted =>
      log.debug("Recovery completed for persistence-id {}", persistenceId)

  }

  override def receiveCommand: Receive = {
    //case GetSequenceNo(_) => sender() ! SequenceNo(lastSequenceNr)
    case cmd: Command =>
      log.debug("persistence-id {} receive command {}", persistenceId, cmd)
      val action = service.commandReceive(serviceContext)(data)(cmd)
      log.debug("persistence-id {} command {} results to action {}", persistenceId, cmd, action)
      if (action.nonEmpty) {
        persistAll(action.events) { evt =>
          log.debug("persistence-id {} command {} event {}", persistenceId, cmd, evt)
          data = service.eventReceive(serviceContext)(data)(evt)
          log.debug("persistence-id {} at lastSequenceNo {}; snapshotInterval at {} ; data defined {}", persistenceId, lastSequenceNr, snapshotInterval, Option(data).isDefined)
          if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0 && Option(data).isDefined) {
            saveSnapshot(data)
            log.debug("persistence-id {} snapshot done", persistenceId)
          }
        }

        action.onReply(serviceContext, cmd, action.events, data)
      } else action.onReply(serviceContext, cmd, action.events, data)
    case ReceiveTimeout =>
      log.debug("persistence-id {} receive timeout.", persistenceId)
      onTimeout()
    case Hyperion.Stop =>
      log.debug("persistence-id {} is shutting down.", persistenceId)
      context.stop(self)
      log.debug("persistence-id {} is down.", persistenceId)
  }
}
