package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, SupervisorStrategy}

abstract class ServiceActor extends Actor with ActorLogging {

  val serviceContext = new ServiceContext(context, log)

  def serviceReceive: ServiceReceive

  def receive: Receive = serviceReceive(serviceContext)

}

final class ReceivableServiceActor(service: ReceivableService) extends ServiceActor {

  override def serviceReceive: ServiceReceive = service.receive

  override def supervisorStrategy: SupervisorStrategy = service.supervisorStrategy

  override def preStart(): Unit = service.preStart(serviceContext)

  override def postStop(): Unit = service.postStop(serviceContext)

}