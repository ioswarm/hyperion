package de.ioswarm.hyperion.demo

import de.ioswarm.hyperion.Hyperion

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

object Demo1 extends App {

  import scala.concurrent.ExecutionContext.Implicits.global
  import de.ioswarm.hyperion.Implicits._

  implicit val hy: Hyperion = Hyperion("demo")

  val test1 = "test1" receive { _ => {
    case s: String => println(s"Received-String: $s")
    case i: Int => println(s"Received-Int: $i")
  }} route { ref =>
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.model.StatusCodes._

    pathPrefix("test1") {
      get {
        pathPrefix(IntNumber) { i =>
          ref ! i
          complete(OK)
        } ~
        pathPrefix(Segment) { s =>
          ref ! s
          complete(OK)
        }
      }
    }
  }

  val test2 = "test2" receive { _ => {
    case d: Double => println(s"Receive-Double: $d")
  }}

  val trefh = hy.system.actorSelection(hy.system / "hyperion")
  val tref1 = hy.system.actorSelection(hy.system / "hyperion" / "test1")
  val tref2 = hy.system.actorSelection(hy.system / "hyperion" / "test2")

  hy.start(test1) onComplete {
    case Success(_) =>
      println("ready")
      tref1 ! "Hello World!"
      tref1 ! 42
  }

  test2.run onComplete {
    case Success(_) =>
      println("test2 started ...")
      tref2 ! 293.0322
  }

  Await.result(hy.whenTerminated, Duration.Inf)

}
