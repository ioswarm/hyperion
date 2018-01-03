package de.ioswarm.hyperion.dispatcher

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.event.Logging._

final class LoggingDispatcher extends Actor with ActorLogging {

  import de.ioswarm.hyperion.model.LogEntry._

  private val events = context.system.eventStream

//  val cluster = Cluster(context.system)
  val systemName: String = context.system.name
//  val address: String = cluster.selfAddress.toString

  def receive: Receive = {
    case InitializeLogger(_) =>
      log.debug("LoggingDispatcher initialized.")
      sender() ! LoggerInitialized
    case Error(cause, logSource, logClass, message) => events.publish(ERROR(logSource, logClass, message, Some(cause), Some(systemName), None))
    case Warning(logSource, logClass, message) => events.publish(WARN(logSource, logClass, message, None, Some(systemName), None))
    case Info(logSource, logClass, message) => events.publish(INFO(logSource, logClass, message, None, Some(systemName), None))
    case Debug(logSource, logClass, message) => events.publish(DEBUG(logSource, logClass, message, None, Some(systemName), None))
    case _ =>
  }

}
