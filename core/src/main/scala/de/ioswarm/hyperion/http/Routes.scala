package de.ioswarm.hyperion.http

import akka.actor.ActorRef
import akka.http.scaladsl.server.directives.{BasicDirectives, CacheConditionDirectives, CodingDirectives, CookieDirectives, DebuggingDirectives, ExecutionDirectives, FileAndResourceDirectives, FileUploadDirectives, FormFieldDirectives, FramedEntityStreamingDirectives, FutureDirectives, HeaderDirectives, HostDirectives, MarshallingDirectives, MethodDirectives, MiscDirectives, ParameterDirectives, PathDirectives, RangeDirectives, RespondWithDirectives, RouteDirectives, SchemeDirectives, SecurityDirectives, TimeoutDirectives, WebSocketDirectives}
import akka.http.scaladsl.server.{Directive1, PathMatcher, PathMatchers, Route, RouteConcatenation}
import akka.stream.ActorMaterializer
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
  with FramedEntityStreamingDirectives
{

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

  def extractContext: Directive1[RequestContext] = extract{ ctx =>
    new RequestContext(ctx, ctx.materializer.asInstanceOf[ActorMaterializer].system)
  }

  /* Methods */
  def route[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = extractRequestContext { ctx =>
    extractActorSystem { sys =>
      path(pm).tapply(f(new RequestContext(ctx, sys)))
    }
  }

  def GET[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = get {
    route(pm)(f)
  }

  def POST[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = post {
    route(pm)(f)
  }

  def PUT[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = put {
    route(pm)(f)
  }

  def PATCH[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = patch {
    route(pm)(f)
  }

  def HEAD[L](pm:PathMatcher[L])(f:RequestContext => L => Route): Route = head {
    route(pm)(f)
  }

  def OPTIONS[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = options {
    route(pm)(f)
  }

  def DELETE[L](pm: PathMatcher[L])(f: RequestContext => L => Route): Route = delete {
    route(pm)(f)
  }

}

object Routes extends Routes
