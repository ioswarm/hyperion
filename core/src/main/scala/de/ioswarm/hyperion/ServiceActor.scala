package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Terminated}


/**
  * Created by andreas on 31.10.17.
  */
abstract class ServiceActor[T](service: Service[T]) extends Actor with ActorLogging {

  import de.ioswarm.hyperion.ServiceCommands._

  implicit val serviceContext = new Context(context, log)

  context.parent ! Register(self)
  log.info("ServiceActor '"+context.self.path+"' created.")

  def actorReceive: Receive = {
    case r: Register => context.parent ! r
    case Stop =>
      if (context.children.nonEmpty) context.stop(self)
      else {
        context.become(actorStop)
        context.children.foreach(ref => ref ! Stop)
      }
  }

  def actorStop: Receive = {
    case Terminated(_) =>
      if (context.children.isEmpty) context.stop(self)
  }

  override def receive: Receive = actorReceive orElse service.receive(serviceContext)

}

private[hyperion] class DefaultServiceActor(service: Service[NotUsed]) extends ServiceActor(service)
