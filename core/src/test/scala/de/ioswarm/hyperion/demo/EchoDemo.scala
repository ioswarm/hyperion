package de.ioswarm.hyperion.demo

import de.ioswarm.hyperion.App

object EchoDemo extends App {

  import de.ioswarm.hyperion.Implicits._

  run {
    "echo" receive { implicit ctx => {
      case a: Any =>
        ctx.log.info("Receive '{}'", a)
        ctx.sender() ! a
    }
    } route { ref =>
      import de.ioswarm.hyperion.http.Routes._

      GET("echo" / Segment, cors = true) { ctx =>
        t =>
          askService(ref -> t._1){ a => ctx.complete(a.toString) }
      }
    }
  }

}
