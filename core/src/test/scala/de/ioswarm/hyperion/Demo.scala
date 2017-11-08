package de.ioswarm.hyperion

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by andreas on 31.10.17.
  */
object Demo extends App {

  implicit val hyperion = Hyperion("test")

  val srvs = ServiceFactory.create("subtest-01"){ ctx =>
    import ctx._
    {
      case s: String => log.info("subtest-01 receive: "+s)
    }
  } withRoute { ref =>
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.model.StatusCodes._

    pathPrefix("test") {
      pathPrefix(Segment) { s =>
        ref ! s
        complete(OK)
      }
    }
  }

  val srv = ServiceFactory.create("test-01", srvs) { ctx => sub =>
    import ctx._
    {
      case a: Any =>
        log.info("receive something...")
        sub ! a
    }
  } withRoute { ref =>
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.model.StatusCodes._

    pathPrefix("test") {
      pathPrefix(IntNumber) { i =>
        ref ! i
        complete(OK)
      }
    }
  }

  hyperion.run(srv)

  Thread.sleep(10000l)

  hyperion.stop()

  Await.result(hyperion.whenTerminated, Duration.Inf)

}
