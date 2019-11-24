package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging}
import akka.event.EventStream
import de.ioswarm.hyperion.model.{LogLevel, LogMessage}

private[hyperion] class PanicListener extends Actor with ActorLogging {

  val events: EventStream = context.system.eventStream

  override def preStart(): Unit = {
    log.debug("Register for LogMessage")
    events.subscribe(self, classOf[LogMessage])
  }

  override def postStop(): Unit = {
    events.unsubscribe(self)
  }

  def receive: Receive = {
    case LogMessage(_, subject, _, _, _, level, _, _) if level == LogLevel.PANIC =>
      log.info("Receive PANIC '{}' shutting down...", subject)
      context.system.terminate()
    case m @ LogMessage => println(m)
    case _ =>
  }

}
