package de.ioswarm.hyperion.json.argonaut

import argonaut._
import Argonaut._
import de.ioswarm.hyperion.model.{ExceptionMessage, HttpMetric, LogLevel, LogMessage, User}

trait Implicits
  extends de.ioswarm.time.json.Implicits {

  implicit def _hyHttpMetricCodec: CodecJson[HttpMetric] = casecodec14(HttpMetric.apply, HttpMetric.unapply)(
    "timestamp"
    , "subject"
    , "message"
    , "meta"
    , "tags"
    , "ended"
    , "httpProtocol"
    , "httpMethod"
    , "scheme"
    , "host"
    , "port"
    , "path"
    , "fragment"
    , "query"
  )

  implicit def _hyLogLevelEncode: EncodeJson[LogLevel] = EncodeJson{
    case LogLevel.PANIC => jString("PANIC")
    case LogLevel.FATAL => jString("FATAL")
    case LogLevel.ERROR => jString("ERROR")
    case LogLevel.WARN => jString("WARN")
    case LogLevel.INFO => jString("INFO")
    case LogLevel.DEBUG => jString("DEBUG")
    case _ => jString("UNKNOWN")
  }

  implicit def _hyLogLevelDecode: DecodeJson[LogLevel] = implicitly[DecodeJson[String]].map{
    case "PANIC" => LogLevel.PANIC
    case "FATAL" => LogLevel.FATAL
    case "ERROR" => LogLevel.ERROR
    case "WARN" => LogLevel.WARN
    case "INFO" => LogLevel.INFO
    case "DEBUG" => LogLevel.DEBUG
    case _ => LogLevel.UNKNOWN
  }

  implicit def _hyLogMessageCodec: CodecJson[LogMessage] = casecodec8(LogMessage.apply, LogMessage.unapply)(
    "timestamp"
    , "string"
    , "message"
    , "meta"
    , "tags"
    , "logLevel"
    , "clazz"
    , "source"
  )

  implicit def _hyExceptionMessageCodec: CodecJson[ExceptionMessage] = casecodec6(ExceptionMessage.apply, ExceptionMessage.unapply)(
    "timestamp"
    , "string"
    , "message"
    , "meta"
    , "tags"
    , "logLevel"
  )

  implicit def _hyUserMessageCodec: CodecJson[User] = casecodec2(User.apply, User.unapply)(
    "username"
    , "meta"
  )

}
