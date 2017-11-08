package de.ioswarm.hyperion

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer

/**
  * Created by andreas on 31.10.17.
  */
private[hyperion] class Context(actorContext: ActorContext, actorLogger: LoggingAdapter) {
  implicit def context: ActorContext = actorContext
  implicit def log: LoggingAdapter = actorLogger

  def actorOf[T](service: Service[T], watch: Boolean = false): ActorRef = {
    log.info(s"Start child-actor '${service.name}'")
    val ref = context.actorOf(service.props, service.name)
    if (watch) context.watch(ref) // TODO visibility at Termination
    ref
  }

}

private[hyperion] class MaterializedContext(actorContext: ActorContext, actorLogger: LoggingAdapter) extends Context(actorContext, actorLogger) {

  implicit val mat = ActorMaterializer()

}