package de.ioswarm.hyperion.cassandra.model

import de.ioswarm.cassie.Cluster.Connection
import de.ioswarm.cassie._
import de.ioswarm.hyperion.model.{HttpMetricEvent, LogEvent, MetricEvent}

trait HyperionFormats {

  implicit def logTable(implicit con: Connection): Table[LogEvent] = Table("logs", LogEvent.apply _, LogEvent.unapply _)(
    "type" index "LogTypeIndex"
    , "timestamp" asc()
    , "source"
    , "class"
    , "message"
    , "cause"
    , "systemName" pk()
    , "address" asc()
  ).create

  implicit def metricTable(implicit con: Connection): Table[MetricEvent] = Table("metics", MetricEvent.apply _, MetricEvent.unapply _)(
    "timestamp" asc()
    , "heapMemoryUsed"
    , "heapMemoryMax"
    , "cpuCombined"
    , "processors"
    , "systemLoadAverage"
    , "heapMemoryCommitted"
    , "cpuStolen"
    , "systemName" pk()
    , "address" asc()
  ).create

  /*implicit def httpMetricTable(implicit con: Connection): Table[HttpMetricEvent] = Table("httpmetrics", HttpMetricEvent.apply _, HttpMetricEvent.unapply _)(
    "timestamp" asc()
    , "request"
    , "responseTimestamp"
    , "response"
    , "systemName" pk()
    , "address" asc()
  )*/

}
