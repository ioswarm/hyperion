package de.ioswarm.hyperion.logging

import akka.actor.{Actor, ActorLogging}
import akka.event.EventStream
import akka.event.Logging._

class LogListener extends Actor with ActorLogging {

  import de.ioswarm.hyperion.model.LogEntry._

  val events: EventStream = context.system.eventStream

  def receive: Receive = {
    case InitializeLogger(_) =>
      log.debug("LoggingDispatcher initialized.")
      sender() ! LoggerInitialized
    case Error(cause, logSource, logClass, message) => events.publish(ERROR(logSource, logClass, message, Some(cause)))
    case Warning(logSource, logClass, message) => events.publish(WARN(logSource, logClass, message, None))
    case Info(logSource, logClass, message) => events.publish(INFO(logSource, logClass, message, None))
    case Debug(logSource, logClass, message) => events.publish(DEBUG(logSource, logClass, message, None))
    case _ =>
  }

}
