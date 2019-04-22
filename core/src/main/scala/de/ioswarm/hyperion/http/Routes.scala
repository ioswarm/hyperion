package de.ioswarm.hyperion.http

import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.{BasicDirectives, CacheConditionDirectives, CodingDirectives, CookieDirectives, DebuggingDirectives, ExecutionDirectives, FileAndResourceDirectives, FileUploadDirectives, FormFieldDirectives, FramedEntityStreamingDirectives, FutureDirectives, HeaderDirectives, HostDirectives, MarshallingDirectives, MethodDirectives, MiscDirectives, ParameterDirectives, PathDirectives, RangeDirectives, RespondWithDirectives, RouteDirectives, SchemeDirectives, SecurityDirectives, TimeoutDirectives, WebSocketDirectives}
import akka.http.scaladsl.server.{Directive0, Directive1, PathMatcher, PathMatchers, Route, RouteConcatenation}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.Try

trait Routes extends PathMatchers
  with RouteConcatenation
  with BasicDirectives
  with CacheConditionDirectives
  with CookieDirectives
  with DebuggingDirectives
  with CodingDirectives
  with ExecutionDirectives
  with FileAndResourceDirectives
  with FileUploadDirectives
  with FormFieldDirectives
  with FutureDirectives
  with HeaderDirectives
  with HostDirectives
  with MarshallingDirectives
  with MethodDirectives
  with MiscDirectives
  with ParameterDirectives
  with TimeoutDirectives
  with PathDirectives
  with RangeDirectives
  with RespondWithDirectives
  with RouteDirectives
  with SchemeDirectives
  with SecurityDirectives
  with WebSocketDirectives
  with FramedEntityStreamingDirectives {


  /* CORS */
  private val CORSHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`("Authorization","Content-Type", "X-Requested-With")
  )

  private def withCORSHeaders: Directive0 = {
    respondWithHeaders(CORSHeaders)
  }

  def CORS(r: Route): Route = withCORSHeaders {
    import akka.http.scaladsl.model.StatusCodes

    options {
      complete(HttpResponse(StatusCodes.OK).withHeaders(`Access-Control-Allow-Methods`(HttpMethods.OPTIONS, HttpMethods.POST, HttpMethods.PUT, HttpMethods.GET, HttpMethods.DELETE)))
    } ~ r
  }

  /* Directives */
  def askService(mag: (ActorRef, Any), duration: FiniteDuration = 2.seconds): Directive1[Any] = askSuccess(mag, duration)

  def askSuccess(mag: (ActorRef, Any), duration: FiniteDuration = 2.seconds): Directive1[Any] = {
    import akka.pattern.ask

    implicit val timeout: Timeout = Timeout(duration)

    onSuccess(mag._1 ? mag._2)
  }

  def askComplete(mag: (ActorRef, Any), duration: FiniteDuration = 2.seconds): Directive1[Try[Any]] = {
    import akka.pattern.ask

    implicit val timeout: Timeout = Timeout(duration)

    onComplete(mag._1 ? mag._2)
  }


  /* Methods */
  def route[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = extractRequestContext { ctx =>
    extractActorSystem { sys =>
      val rt = path(pm).tapply(f(new RequestContext(ctx, sys)))

      if (cors) CORS(rt) else rt
    }
  }

  def GET[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = get {
    route(pm, cors)(f)
  }

  def POST[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = post {
    route(pm, cors)(f)
  }

  def PUT[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = put {
    route(pm, cors)(f)
  }

  def PATCH[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = patch {
    route(pm, cors)(f)
  }

  def HEAD[L](pm:PathMatcher[L], cors: Boolean = false)(f:RequestContext => L => Route): Route = head {
    route(pm, cors)(f)
  }

  def OPTIONS[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = options {
    route(pm, cors)(f)
  }

  def DELETE[L](pm: PathMatcher[L], cors: Boolean = false)(f: RequestContext => L => Route): Route = delete {
    route(pm, cors)(f)
  }

}

object Routes extends Routes
