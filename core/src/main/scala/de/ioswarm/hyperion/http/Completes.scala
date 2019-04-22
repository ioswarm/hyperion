package de.ioswarm.hyperion.http

import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route

trait Completes {

  import akka.http.scaladsl.server.Directives
  import akka.http.scaladsl.model.StatusCodes


  def reject: Route = Directives.reject
  def redirect(uri: Uri): Route = Directives.redirect(uri, StatusCodes.MovedPermanently)
  def complete(m: ⇒ ToResponseMarshallable): Route = Directives.complete(m)
  def fail(t: Throwable): Route = Directives.failWith(t)

  def OK: Route = complete(StatusCodes.OK)
  def OK[T](t: T)(implicit m: ToEntityMarshaller[T]): Route = complete(StatusCodes.OK, t)

  // TODO impl rest of status-codes
}