package de.ioswarm.hyperion.model

import argonaut._
import Argonaut._

import java.time.Instant

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

object HttpMetricEvent {

  def apply(requestMillis: Long, request: HttpRequest, responseMillis: Option[Long] = None, response: Option[HttpResponse] = None, systemName: Option[String] = None, address: Option[String] = None): HttpMetricEvent = HttpMetricEvent(
    Instant.ofEpochMilli(requestMillis)
    , request
    , responseMillis.map(m => Instant.ofEpochMilli(m))
    , response
    , systemName
    , address
  )

  import de.ioswarm.hyperion.json.Codecs._

  implicit def httpMetricEventEncoder: EncodeJson[HttpMetricEvent] = EncodeJson(m =>
    ("requestTimestamp" := m.requestTimestamp) ->:
      ("request" := m.request) ->:
      ("responseTimestamp" :=? m.responseTimestamp) ->?:
      ("response" :=? m.response) ->?:
      ("systemName" :=? m.systemName) ->?:
      ("address" :=? m.address) ->?:
      ("millis" := m.millis) ->:
    jEmptyObject
  )

}
case class HttpMetricEvent(
                          requestTimestamp: Instant
                          , request: HttpRequest
                          , responseTimestamp: Option[Instant]
                          , response: Option[HttpResponse]
                          , systemName: Option[String]
                          , address: Option[String]
                          ) extends Event {

  def millis: Long = responseTimestamp.getOrElse(Instant.now()).toEpochMilli - requestTimestamp.toEpochMilli

}
