package de.ioswarm.hyperion.http

import akka.http.scaladsl.server.{Directive0, ExceptionHandler, Route}

object Middlewares {

  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.model.headers._
  import akka.http.scaladsl.model.{HttpMethods, HttpResponse}

  /* CORS */
  private val CORSHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`("Authorization","Content-Type", "X-Requested-With")
  )

  private def withCORSHeaders: Directive0 = {
    respondWithHeaders(CORSHeaders)
  }

  val cors: Middleware = { route =>
    withCORSHeaders {
      import akka.http.scaladsl.model.StatusCodes

      options {
        complete(HttpResponse(StatusCodes.OK).withHeaders(`Access-Control-Allow-Methods`(HttpMethods.OPTIONS, HttpMethods.POST, HttpMethods.PUT, HttpMethods.GET, HttpMethods.DELETE)))
      } ~ route
    }
  }


  val exceptionHandler: Middleware = { route =>
    val handler = ExceptionHandler{
      case t: Throwable =>
        extractUri { uri =>
          println(s"ERROR in $uri")
          complete(StatusCodes.InternalServerError, "A little Error")
        }
    }

    handleExceptions(handler) {
      route
    }
  }

}
