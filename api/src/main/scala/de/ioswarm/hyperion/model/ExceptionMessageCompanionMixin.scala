package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

trait ExceptionMessageCompanionMixin {

  import de.ioswarm.time.json.Implicits._

  implicit def _hyExceptionMessageCodec: CodecJson[ExceptionMessage] = casecodec6(ExceptionMessage.apply, ExceptionMessage.unapply)(
    "timestamp"
    , "string"
    , "message"
    , "meta"
    , "tags"
    , "logLevel"
  )

}
