package de.ioswarm.hyperion.logging

import java.io.{PrintWriter, StringWriter}

import akka.actor.{Actor, ActorLogging}
import akka.event.EventStream
import akka.event.Logging.{Debug, Error, Info, InitializeLogger, LoggerInitialized, Warning}
import de.ioswarm.hyperion.HYException
import de.ioswarm.hyperion.model.{LogLevel, LogMessage}
import de.ioswarm.time.DateTime

class LogListener extends Actor with ActorLogging {

  val events: EventStream = context.system.eventStream

  def stackTrace(t: Throwable): String = {
    val strw = new StringWriter()
    val prnw = new PrintWriter(strw, true)
    try {
      t.printStackTrace(prnw)
      strw.getBuffer.toString
    } finally {
      prnw.close()
      strw.close()
    }
  }

  def createLogMessage(source: String, clazz: Class[_], message: String, cause: Option[Throwable], logLevel: LogLevel): LogMessage = cause match {
    case Some(t) =>
      val msg = stackTrace(t)
      t match {
        case e: HYException =>
          LogMessage(
            e.timestamp
            , message
            , None
            , List(e.errorCode.map(code => "errorCode" -> code)).flatten.toMap
            , Set.empty
            , e.level
            , Some(source)
            , Some(clazz.getName)
          )
        case _ => LogMessage(
          DateTime()
          , message
          , Some(msg)
          , Map.empty
          , Set.empty
          , logLevel
          , Some(source)
          , Some(clazz.getName)
        )
      }
    case None =>
      LogMessage(
        DateTime()
        , message
        , None
        , Map.empty
        , Set.empty
        , logLevel
        , Some(source)
        , Some(clazz.getName)
      )
  }

  def receive: Receive = {
    case InitializeLogger(_) =>
      log.debug("LogListener initialized.")
      sender() ! LoggerInitialized
    case Error(cause, logSource, logClass, message) => events.publish(createLogMessage(logSource, logClass, message.toString, Some(cause), LogLevel.ERROR))
    case Warning(logSource, logClass, message) => events.publish(createLogMessage(logSource, logClass, message.toString, None, LogLevel.WARN))
    case Info(logSource, logClass, message) => events.publish(createLogMessage(logSource, logClass, message.toString, None, LogLevel.INFO))
    case Debug(logSource, logClass, message) => events.publish(createLogMessage(logSource, logClass, message.toString, None, LogLevel.DEBUG))
    case _ =>
  }

}
