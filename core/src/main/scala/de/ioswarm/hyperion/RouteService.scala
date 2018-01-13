package de.ioswarm.hyperion

import java.net.InetSocketAddress

import akka.pattern.pipe
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, RemoteAddress}
import akka.http.scaladsl.model.headers.`Remote-Address`
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Unzip, Zip}
import akka.util.Timeout
import de.ioswarm.hyperion.model.HttpMetricEvent

import scala.concurrent.Future

object RouteService {

  import Implicits._

  def apply(host: String, port: Int, route: Route): ActorService = s"route_${host}_$port" receive Service.emptyBehavior withActor classOf[RouteService] withArgs(host, port, route)

  case object Init
  case object Ack
  case object Complete

}
class RouteService(service: ActorService, host: String, port: Int, route: Route) extends ActorServiceActor(service) {

  import context.dispatcher
  import akka.actor.Status.Failure
  import akka.http.scaladsl.Http
  import RouteService._
  import Hyperion._

  implicit val mat: ActorMaterializer = ActorMaterializer()

  def handleRequest[Mat](remote: InetSocketAddress, handler: Flow[HttpRequest, HttpResponse, Mat]): Flow[HttpRequest, HttpResponse, Mat] = Flow.fromGraph(GraphDSL.create(handler){ implicit b => hdl =>
    import GraphDSL.Implicits._

    val req = b.add(Flow[HttpRequest].map{req =>
      import RemoteAddress._

      val hdrs = Seq("X-Forwarded-For", "Remote-Address", "X-Real-IP").map(_.toLowerCase)
      if (!req.headers.exists(h => hdrs.contains(h.lowercaseName())))
        req.copy(headers = req.headers ++ Seq(
          `Remote-Address`(IP(remote.getAddress, Some(remote.getPort)))
        ))
      else req
    }
      .map(req => (req, (java.lang.System.currentTimeMillis(), req)))
    )

    val uz = b.add(Unzip[HttpRequest, (Long, HttpRequest)])
    val bc = b.add(Broadcast[HttpResponse](2))
    val zip = b.add(Zip[(Long, HttpRequest), HttpResponse])
    val metric = b.add(Flow[((Long, HttpRequest), HttpResponse)].map{ m =>
      HttpMetricEvent.create(m._1._1, m._1._2, Some(java.lang.System.currentTimeMillis()), Some(m._2))
    })


    req ~> uz.in
    uz.out0 ~> hdl ~> bc
    bc.out(1) ~> zip.in1
    uz.out1 ~> zip.in0
    zip.out ~> metric ~> Sink.actorRefWithAck(self, Init, Ack, Complete)

    FlowShape(req.in, bc.out(0))
  })

  val binding: Future[Http.ServerBinding] = Http(context.system).bind(interface = host, port = port).to(sink = Sink foreach { conn =>
    val remote = conn.remoteAddress

    conn.handleWith(handleRequest(remote, Route.handlerFlow(route)))
  })
    .run() pipeTo self

  override def stopService()(implicit timeout: Timeout): Future[Stopped] = for {
    _ <- binding.flatMap(_.unbind())
    se <- super.stopService()
  } yield se

  override def serviceReceive: Receive = {
    case Http.ServerBinding(address) =>
      log.info("Route-service {} at {} listen on {}", service.name, self.path, address)

    case Failure(e) =>
      log.error(e, "Route-service {} at {} could not bind to {}:{} ... stop self", service.name, self.path, host, port)
      context.stop(self)

    case Init => sender() ! Ack
    case m: HttpMetricEvent =>
      val repl = sender()
      context.system.eventStream.publish(m)
      repl ! Ack
    case Complete => // DO NOTHING JET

  }

}
