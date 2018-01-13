package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._
import java.time.Instant

object LogEvent {

  def create(e: LogEntry, systemName: Option[String], address: Option[String]): LogEvent = LogEvent(
    e.logType
    , e.timestamp
    , e.source
    , e.clazz
    , e.message
    , e.cause
    , systemName
    , address
  )

  import de.ioswarm.hyperion.json.Codecs._

  implicit def logEventCodec: CodecJson[LogEvent] = casecodec8(LogEvent.apply, LogEvent.unapply)(
    "type"
    , "timestamp"
    , "source"
    , "class"
    , "message"
    , "cause"
    , "systemName"
    , "address"
  )
}
case class LogEvent(
                     logType: String
                     , timestamp: Instant
                     , source: String
                     , clazz: String
                     , message: String
                     , cause: Option[String]
                     , systemName: Option[String]
                     , address: Option[String]
                   ) extends Event {

}
