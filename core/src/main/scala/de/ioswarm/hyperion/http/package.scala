package de.ioswarm.hyperion

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives

import scala.concurrent.Future

package object http {

  type ContextualAuthenticator[T] = RequestContext => SecurityDirectives.AsyncAuthenticator[T]
  type Middleware = Route => Route

  def noneAuthenticator[T]: ContextualAuthenticator[T] = _ => _ => Future.successful(None)



  implicit def _route2Middleware(route: Route): Middleware = {
    import akka.http.scaladsl.server.Directives._
    r => r ~ route
  }

}
