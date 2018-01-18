package de.ioswarm.hyperion

import akka.actor.{ActorContext, ActorPath, ActorRef, ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContextExecutor

class ServiceContext(actorContext: ActorContext, actorLogger: LoggingAdapter) {

  implicit def context: ActorContext = actorContext
  implicit def log: LoggingAdapter = actorLogger

  implicit def dispatcher: ExecutionContextExecutor = actorContext.dispatcher

  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()

  def config: Config = context.system.settings.config

  def actorOf(service: Service): ActorRef = {
    log.debug(s"Context start child-actor '${service.name}'")
    context.child(service.name) match {
      case Some(ref) => ref
      case None => service.createActor(actorContext)
    }
  }

  def system: ActorSystem = context.system

  def serviceSelection(path: ActorPath): ActorSelection = system.actorSelection(system / "hyperion" / path.elements)
  def serviceSelection(elements: String*): ActorSelection = system.actorSelection(system / "hyperion" / elements)

  def sender(): ActorRef = context.sender()

  //def self(): ActorRef = context.self
  implicit final val self: ActorRef = context.self

}

