package de.ioswarm.hyperion.json

import java.time.Instant

import argonaut._
import Argonaut._
import akka.http.scaladsl.model._

object Codecs {

  implicit def instantEncoder: EncodeJson[Instant] = EncodeJson(d =>
    jString(d.toString)
  )

  implicit def instantDecoder: DecodeJson[Instant] = implicitly[DecodeJson[String]].map(s =>
    Instant.parse(s)
  )


  // AKKA HTTP Stuff
  implicit def httpMethodEncoder: EncodeJson[HttpMethod] = EncodeJson(m =>
    jString(m.value)
  )

  implicit def uriEncoder: EncodeJson[Uri] = EncodeJson(u =>
    jString(u.toString())
  )

  implicit def uriDecoder: DecodeJson[Uri] = implicitly[DecodeJson[String]].map(s =>
    Uri(s)
  )

  implicit def httpHeaderEncoder: EncodeJson[HttpHeader] = EncodeJson(h =>
    ("name" := h.name) ->: ("value" := h.value) ->: jEmptyObject
  )

  implicit def httpProtocolEncoder: EncodeJson[HttpProtocol] = EncodeJson(p =>
    jString(p.value)
  )

  implicit def mediaTypeEncoder: EncodeJson[MediaType] = EncodeJson(m =>
    jString(s"${m.mainType}/${m.subType}")
  )

  implicit def httpCharsetEncoder: EncodeJson[HttpCharset] = EncodeJson( c =>
    jString(s"${c.value}")
  )

  implicit def contentTypeEncoder: EncodeJson[ContentType] = EncodeJson( c =>
    ("mediaType" := c.mediaType) ->: ("charset" :=? c.charsetOption) ->?: jEmptyObject
  )

  implicit def statusCodeEncoder: EncodeJson[StatusCode] = EncodeJson( s =>
    ("code" := s.intValue) ->: ("reason" := s.reason) ->: jEmptyObject
  )

  implicit def httpRequestEncoder: EncodeJson[HttpRequest] = EncodeJson( r =>
    ("method" := r.method) ->:
      ("protocol" := r.protocol) ->:
      ("uri" := r.uri) ->:
      ("contentType" := r.entity.contentType) ->:
      ("length" :=? r.entity.contentLengthOption) ->?:
      ("headers" := r.headers.toVector) ->:
      jEmptyObject
  )

  implicit def httpResponseEncoder: EncodeJson[HttpResponse] = EncodeJson( r =>
    ("statusCode" := r.status) ->:
      ("protocol" := r.protocol) ->:
      ("contentType" := r.entity.contentType) ->:
      ("length" :=? r.entity.contentLengthOption) ->?:
      ("headers" := r.headers.toVector) ->:
      jEmptyObject
  )

}
