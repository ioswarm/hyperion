package de.ioswarm.hyperion

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

class ServiceContext(actorContext: ActorContext, actorLogger: LoggingAdapter) {

  implicit def context: ActorContext = actorContext
  implicit def log: LoggingAdapter = actorLogger

  def config: Config = context.system.settings.config

  def actorOf(service: Service): ActorRef = {
    log.info(s"Start child-actor '${service.name}'")
    context.child(service.name) match {
      case Some(ref) => ref
      case None => context.actorOf(service.props, service.name)
    }
  }

  def sender(): ActorRef = context.sender()

}

class MaterializedServiceContext(actorContext: ActorContext, actorLogger: LoggingAdapter) extends ServiceContext(actorContext, actorLogger) {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

}
