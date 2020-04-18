package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

trait LogMessageCompanionMixin {

  import de.ioswarm.time.json.Implicits._

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

}
