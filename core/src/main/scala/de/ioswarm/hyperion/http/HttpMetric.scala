package de.ioswarm.hyperion.http

import java.time.LocalDateTime

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.Uri.{Authority, Host, Path}
import de.ioswarm.hyperion.model.Message

object HttpMetric{

  def apply(startTime: LocalDateTime, endTime: LocalDateTime, request: HttpRequest, response: HttpResponse): HttpMetric = HttpMetric(
    startTime
    , endTime
    , request.protocol.value
    , request.method.value
    , request.uri.scheme
    , request.uri.authority.host.address()
    , request.uri.authority.port
    , request.uri.path.toString()
    , request.uri.fragment
    , request.uri.rawQueryString
    , request.headers.map(h => s"request.${h.name()}" -> h.value()).toMap ++ response.headers.map(h => s"response.${h.name()}" -> h.value).toMap
    , Set.empty
  )

}
case class HttpMetric(
                       startTime: LocalDateTime
                       , endTime: LocalDateTime
                       , httpProtocol: String
                       , httpMethod: String
                       , scheme: String
                       , host: String
                       , port: Int
                       , path: String
                       , fragment: Option[String]
                       , query: Option[String]
                       , meta: Map[String, Any]
                       , tags: Set[String]
                     ) extends Message {

  import de.ioswarm.hyperion.utils.Time._

  def uri: Uri = Uri(scheme = this.scheme, authority = Authority(Host(this.host), this.port), path = Path(this.path), fragment = this.fragment, queryString = this.query)
  def time: LocalDateTime = startTime
  def message: Any = toString

  override def toString: String = s"$httpMethod $uri in ${endTime.toUtcMillis-startTime.toUtcMillis} ms"

}
