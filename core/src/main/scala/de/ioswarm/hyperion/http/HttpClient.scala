package de.ioswarm.hyperion.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest, HttpResponse, RequestEntity, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import de.ioswarm.hyperion.AkkaProvider

import scala.concurrent.Future
import scala.language.implicitConversions

object HttpClient {

  implicit def _hyperionAkkaProviderToActorSystem(p: AkkaProvider): ActorSystem = p.system
  //implicit def _hyperionAkkaProviderToMaterializer(p: AkkaProvider): Materializer = ActorMaterializer()(p.system)

  implicit class _httpClientUriExtender(val uri: Uri) extends AnyVal {

    def request(method: HttpMethod)(implicit system: ActorSystem): Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = uri, method = method))
    def request[V](value: V, method: HttpMethod)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = {
      import system.dispatcher
      for {
        reqEntiy <- Marshal(value).to[RequestEntity]
        response <- Http().singleRequest(HttpRequest(uri = uri, method = method, entity = reqEntiy))
      } yield response
    }

    def get(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.GET)
    def get[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.GET)

    def post(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.POST)
    def post[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.POST)

    def put(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.PUT)
    def put[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.PUT)

    def delete(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.DELETE)
    def delete[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.DELETE)

  }

  implicit class _httpClientStringExtender(val s: String) extends AnyVal {

    def request(method: HttpMethod)(implicit system: ActorSystem): Future[HttpResponse] = Uri(s).request(method)
    def request[V](value: V, method: HttpMethod)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = Uri(s).request(value, method)

    def get(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.GET)
    def get[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.GET)

    def post(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.POST)
    def post[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.POST)

    def put(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.PUT)
    def put[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.PUT)

    def delete(implicit system: ActorSystem): Future[HttpResponse] = request(HttpMethods.DELETE)
    def delete[V](value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(value, HttpMethods.DELETE)

  }


  implicit class _httpClientHttpResponseExtender(val resp: HttpResponse) extends AnyVal {

    def asEither[V](implicit system: ActorSystem, mat: Materializer, um: Unmarshaller[ResponseEntity, V]): Future[Either[HttpResponse, V]] = {
      import mat.executionContext
      resp.status.intValue() match {
        case 200 => Unmarshal(resp.entity).to[V].map(Right(_))
        case 201 => Unmarshal(resp.entity).to[V].map(Right(_))
        case _ => Future.successful(Left(resp))
      }
    }

    def as[V](implicit system: ActorSystem, mat: Materializer, um: Unmarshaller[ResponseEntity, V]): Future[Option[V]] = {
      import mat.executionContext
      asEither.map(_.toOption)
    }

  }

  implicit class _httpClientFutureHttpResponseExtender(val fut: Future[HttpResponse]) extends AnyVal {

    def asEither[V](implicit system: ActorSystem, mat: Materializer, um: Unmarshaller[ResponseEntity, V]): Future[Either[HttpResponse, V]] = {
      import mat.executionContext
      fut.flatMap(_.asEither)
    }

    def as[V](implicit system: ActorSystem, mat: Materializer, um: Unmarshaller[ResponseEntity, V]): Future[Option[V]] = {
      import mat.executionContext
      fut.flatMap(_.as)
    }
  }

  def request(uri: Uri, method: HttpMethod)(implicit system: ActorSystem): Future[HttpResponse] = uri.request(method)
  def request[V](uri: Uri, value: V, method: HttpMethod)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = uri.request(value, method)

  def get(uri: Uri)(implicit system: ActorSystem): Future[HttpResponse] = request(uri, HttpMethods.GET)
  def get[V](uri: Uri, value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(uri, value, HttpMethods.GET)

  def post(uri: Uri)(implicit system: ActorSystem): Future[HttpResponse] = request(uri, HttpMethods.POST)
  def post[V](uri: Uri, value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(uri, value, HttpMethods.POST)

  def put(uri: Uri)(implicit system: ActorSystem): Future[HttpResponse] = request(uri, HttpMethods.PUT)
  def put[V](uri: Uri, value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(uri, value, HttpMethods.PUT)

  def delete(uri: Uri)(implicit system: ActorSystem): Future[HttpResponse] = request(uri, HttpMethods.DELETE)
  def delete[V](uri: Uri, value: V)(implicit system: ActorSystem, marshaller: Marshaller[V, RequestEntity]): Future[HttpResponse] = request(uri, value, HttpMethods.DELETE)

}
