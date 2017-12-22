package de.ioswarm.hyperion.demo

import akka.util.Timeout
import de.ioswarm.hyperion.Hyperion

import scala.util.Success

/**
  * Created by apape on 22.12.17.
  */
object Demo01 extends App {

  import de.ioswarm.hyperion.Implicits._

  val hy = Hyperion("demo")

  val srv1 = "test1" withReceive { ctx =>
    {
      case s: String =>
        ctx.log.info("test1 receive '{}'", s)
        val repl = ctx.sender()
        repl ! s.toUpperCase
    }
  } withRoute { ref =>
    import scala.concurrent.duration._
    import akka.pattern.ask
    import akka.http.scaladsl.model.StatusCodes.InternalServerError
    import akka.http.scaladsl.server.Directives._

    implicit val timeout: Timeout = Timeout(10.seconds)

    pathPrefix("test1") {
      pathPrefix(Segment) { s =>
        onComplete(ref ? s) {
          case Success(s: String) => complete(s)
          case _ => complete(InternalServerError)
        }
      }
    }

  }

  hy.start(srv1)
}
