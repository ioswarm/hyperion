package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._
import java.io.{PrintWriter, StringWriter}
import java.time.{Instant, LocalDateTime, ZoneId}

object LogEntry {

  def apply(logType: String, logSource: String, clazz: Class[_], message: Any, cause: Option[Throwable] = None, systemName: Option[String] = None, address: Option[String] = None): LogEntry = LogEntry(
    logType
    , LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant
    , logSource
    , clazz.getName
    , message.toString
    , cause.map{t =>
      val strw = new StringWriter()
      val prnw = new PrintWriter(strw, true)
      t.printStackTrace(prnw)
      strw.getBuffer.toString
    }
    , systemName
    , address
  )

  def ERROR(logSource: String, clazz: Class[_], message: Any, cause: Option[Throwable] = None, systemName: Option[String] = None, address: Option[String] = None) = apply(
    "ERROR"
    , logSource
    , clazz
    , message
    , cause
    , systemName
    , address
  )

  def WARN(logSource: String, clazz: Class[_], message: Any, cause: Option[Throwable] = None, systemName: Option[String] = None, address: Option[String] = None) = apply(
    "WARN"
    , logSource
    , clazz
    , message
    , cause
    , systemName
    , address
  )

  def INFO(logSource: String, clazz: Class[_], message: Any, cause: Option[Throwable] = None, systemName: Option[String] = None, address: Option[String] = None) = apply(
    "INFO"
    , logSource
    , clazz
    , message
    , cause
    , systemName
    , address
  )

  def DEBUG(logSource: String, clazz: Class[_], message: Any, cause: Option[Throwable] = None, systemName: Option[String] = None, address: Option[String] = None) = apply(
    "DEBUG"
    , logSource
    , clazz
    , message
    , cause
    , systemName
    , address
  )

  import de.ioswarm.hyperion.json.Codecs._

  implicit def logEntryCodec: CodecJson[LogEntry] = casecodec8(LogEntry.apply, LogEntry.unapply)(
    "type"
    , "timestamp"
    , "source"
    , "class"
    , "massage"
    , "cause"
    , "system"
    , "address"
  )

}
case class LogEntry(logType: String
                    ,timestamp: Instant
                    ,source: String
                    ,clazz: String
                    ,message: String
                    ,cause: Option[String]
                    ,systemName: Option[String]
                    ,address: Option[String]) {

  def isERROR: Boolean = logType == "ERROR"
  def isWARN: Boolean = logType == "WARN"
  def isINFO: Boolean = logType == "INFO"
  def isDEBUG: Boolean = logType == "DEBUG"
  def isUNKNOWN: Boolean = !(isERROR || isWARN || isINFO || isDEBUG)

  def asERROR: LogEntry = copy(logType = "ERROR")
  def asWARN: LogEntry = copy(logType = "WARN")
  def asINFO: LogEntry = copy(logType = "INFO")
  def asDEBUG: LogEntry = copy(logType = "WARN")

}
