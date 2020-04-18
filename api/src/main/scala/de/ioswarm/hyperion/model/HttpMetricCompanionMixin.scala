package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

trait HttpMetricCompanionMixin {

  import de.ioswarm.time.json.Implicits._

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

}
