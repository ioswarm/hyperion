package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._
import java.time.Instant

object MetricEvent {

  import de.ioswarm.hyperion.json.Codecs._

  implicit def metricEventCodec: CodecJson[MetricEvent] = casecodec10(MetricEvent.apply, MetricEvent.unapply)(
    "timestamp"
    , "heapMemoryUsed"
    , "heapMemoryMax"
    , "cpuCombined"
    , "processors"
    , "systemLoadAverage"
    , "heapMemoryCommitted"
    , "cpuStolen"
    , "systemName"
    , "address"
  )

}
case class MetricEvent(timestamp: Instant
                       , heapMemoryUsed: Option[Double]
                       , heapMemoryMax: Option[Double]
                       , cpuCombined: Option[Double]
                       , processors: Option[Double]
                       , systemLoadAverage: Option[Double]
                       , heapMemoryCommitted: Option[Double]
                       , cpuStolen: Option[Double]
                       , systemName: Option[String]
                       , address: Option[String]
                      ) {

}
