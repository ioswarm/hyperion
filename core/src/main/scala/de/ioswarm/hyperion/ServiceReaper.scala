package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.collection.mutable

/**
  * Created by andreas on 07.11.17.
  */
object ServiceReaper {
  def props(coordinator: ActorRef): Props = Props(classOf[ServiceReaper], coordinator)
}
final class ServiceReaper(coordinator: ActorRef) extends Actor with ActorLogging {

  import ServiceCommands._

  val refs: mutable.ArrayBuffer[ActorRef] = mutable.ArrayBuffer.empty[ActorRef]

  def receive: Receive = {
    case WatchService(ref) =>
      context.watch(ref)
      refs += ref
    case Terminated(ref) =>
      refs -= ref
      if (refs.isEmpty) context.system.terminate()
  }

}
