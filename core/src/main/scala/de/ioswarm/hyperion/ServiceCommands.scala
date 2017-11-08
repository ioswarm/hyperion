package de.ioswarm.hyperion

import akka.actor.ActorRef

/**
  * Created by andreas on 05.11.17.
  */
object ServiceCommands {

  final case class Register(ref: ActorRef)

  final case class WatchService(ref: ActorRef)
  case object Stop

  case object StartSubsequentServices

}
