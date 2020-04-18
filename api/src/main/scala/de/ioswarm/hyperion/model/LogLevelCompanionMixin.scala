package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

trait LogLevelCompanionMixin {

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

}
