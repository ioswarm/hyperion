package de.ioswarm.hyperion

import akka.actor.{ActorRef, ActorSystem, Terminated}

import scala.collection.immutable
import scala.concurrent.Future


/**
  * Created by andreas on 05.11.17.
  */
object Hyperion {

  def apply()(implicit system: ActorSystem): Hyperion = new HyperionImpl(system.name, system)
  def apply(name: String): Hyperion = new HyperionImpl(name, ActorSystem(name))

}

trait Hyperion {

  def name: String
  def system: ActorSystem
  def run(services: Service[_]*): ActorRef
  def stop(): Unit

  def terminate(): Future[Terminated] = system.terminate()

  def whenTerminated: Future[Terminated] = system.whenTerminated

}

private[hyperion] class HyperionImpl(val name: String, val system: ActorSystem) extends Hyperion {

  import ServiceCommands._

  def run(services: Service[_]*): ActorRef = {
    val service = ServiceImpl(
      "hyperion"
      , Service.emptyBehavior
      , Service.emptyRoute
      , classOf[ServiceCoordinator]
      , immutable.Seq.empty[Any]
      , services.toList
    )
    system.actorOf(service.props, service.name)
  }

  def stop(): Unit = {
    system.actorSelection("/user/hyperion") ! Stop
  }

}
