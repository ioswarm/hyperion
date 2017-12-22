package de.ioswarm.hyperion

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

private[hyperion] class Context(actorContext: ActorContext, actorLogger: LoggingAdapter) {

  implicit def context: ActorContext = actorContext
  implicit def log: LoggingAdapter = actorLogger

  def config: Config = context.system.settings.config

  def actorOf[T](service: Service[T]): ActorRef = {
    log.info(s"Start child-actor '${service.name}'")
    context.child(service.name) match {
      case Some(ref) => ref
      case None => context.actorOf(service.props, service.name)
    }
  }

  def sender(): ActorRef = context.sender()

}

private[hyperion] class MaterializedContext(actorContext: ActorContext, actorLogger: LoggingAdapter) extends Context(actorContext, actorLogger) {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

}