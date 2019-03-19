package de.ioswarm.hyperion.http

import akka.NotUsed
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Unzip, Zip}
import akka.util.Timeout
import de.ioswarm.hyperion.Hyperion

import scala.concurrent.{Await, Future}
import scala.util.Success

object HttpActor {

  case object Init
  case object Ack
  case object Complete

}
private[hyperion] class HttpActor(host: String, port: Int, route: Route) extends Actor with ActorLogging {

  final class HttpMetrics() extends Actor {

    import HttpActor._

    def receive: Receive = {
      case Init => sender() ! Ack
      case Complete => /* DO NOTHING */
      case hm: HttpMetric => context.system.eventStream.publish(hm)
    }

  }

  import context.dispatcher
  import concurrent.duration._

  implicit val mat: ActorMaterializer = ActorMaterializer()

  implicit val resolveTimeout: Timeout = Timeout(5.seconds)
  val metricsActor: ActorRef = context.actorOf(Props(new HttpMetrics()), "metrics")

  def metrics(route: Route): Flow[HttpRequest, HttpResponse, NotUsed] = Flow.fromGraph(
    GraphDSL.create(Route.handlerFlow(route)){ implicit b => hdl =>
      import GraphDSL.Implicits._
      import  HttpActor._

      val enrich = b.add(Flow[HttpRequest].map{ r => (r, (System.currentTimeMillis(), r))})
      val uz = b.add(Unzip[HttpRequest, (Long, HttpRequest)])
      val bc = b.add(Broadcast[HttpResponse](2))
      val zip = b.add(Zip[(Long, HttpRequest), HttpResponse])
      val metric = b.add(Flow[((Long, HttpRequest), HttpResponse)].map{ r =>
        import de.ioswarm.hyperion.utils.Time._

        val startTime = r._1._1.toUtc
        val endTime = System.currentTimeMillis().toUtc
        val req = r._1._2
        val resp = r._2

        HttpMetric(startTime, endTime, req, resp)
      })

      enrich ~> uz.in
      uz.out0 ~> hdl     ~> bc
      zip.in1 <~ bc.out(1)
      uz.out1 ~> zip.in0
      zip.out ~> metric ~> Sink.foreach(println) //Sink.actorRefWithAck(metricsActor, Init, Ack, Complete)

      FlowShape(enrich.in, bc.out(0))
    }
  )

  val binding: Future[Http.ServerBinding] = Http(context.system).bindAndHandle(
    metrics(route)
    , host
    , port
  ) pipeTo self

  def receive: Receive = {
    case Http.ServerBinding(address) =>
      log.info("HTTP-Server at {} listen on {}", self.path, address)

    case Failure(e) =>
      log.error(e, "HTTP-Server at {} could not bind to {}:{} ... stop self", self.path, host, port)
      context.stop(self)

    case Hyperion.Stop =>
      binding.flatMap(_.unbind()) onComplete {
        case Success(_) =>
          log.info("HTTP-Server at {} bound to {}:{} ... stopped", self.path, host, port)
          context.stop(self)
        case util.Failure(e) =>
          log.error(e, "Could not unbind HTTP-Serve ...")
          context.stop(self)
      }
  }

}
