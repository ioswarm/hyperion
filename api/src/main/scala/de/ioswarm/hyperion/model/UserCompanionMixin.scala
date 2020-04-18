package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

trait UserCompanionMixin {

  implicit def _hyUserMessageCodec: CodecJson[User] = casecodec2(User.apply, User.unapply)(
    "username"
    , "meta"
  )

}
