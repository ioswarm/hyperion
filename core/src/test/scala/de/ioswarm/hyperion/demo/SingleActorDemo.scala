package de.ioswarm.hyperion.demo

import akka.actor.Actor
import de.ioswarm.hyperion.App

object SingleActorDemo extends App {

  import de.ioswarm.hyperion.Implicits._

  final class Echo extends Actor {
    override def receive: Receive = {
      case a: Any => context.sender() ! a
    }
  }

  applyRoute{
    import akka.http.scaladsl.server.Directives._

    pathPrefix("test2" / Segment) { s =>
      complete(s"ECHO: $s")
    }
  }

  run(("echo" withActor classOf[Echo]) route { ref =>
    import akka.pattern.ask
    import akka.util.Timeout
    import concurrent.duration._
    import akka.http.scaladsl.server.Directives._

    pathPrefix("echo" / Segment) { s =>
      get {
        implicit val timeout: Timeout = Timeout(2.seconds)
        onSuccess(ref ? s) {
          a: Any => complete(a.toString)
        }
      }
    }
  })

}
