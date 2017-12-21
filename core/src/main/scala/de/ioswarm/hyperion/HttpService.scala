package de.ioswarm.hyperion

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.Actor
import akka.actor.Status.Failure
import akka.pattern.pipe
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Remote-Address`
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, RemoteAddress}
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Unzip, Zip}

import scala.concurrent.Future
import scala.collection.immutable

object HttpService {

  def apply(host: String, port: Int, route: Route): Service[NotUsed] = DefaultServiceImpl(
    s"http_${host}_$port"
    , Service.emptyBehavior
    , Service.emptyRoute
    , classOf[HttpService]
    , immutable.Seq(host, port, route)
    , None
    , None
    , immutable.Seq.empty[Service[_]]
  )

}
final class HttpService(service: Service[NotUsed], host: String, port: Int, route: Route) extends ServiceActor(service) {

  import ServiceActor._
  import context.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()

  def handleRequest[Mat](remote: InetSocketAddress, millis: Long, handler: Flow[HttpRequest, HttpResponse, Mat]): Flow[HttpRequest, HttpResponse, Mat] = Flow.fromGraph(GraphDSL.create(handler){ implicit b => hdl =>
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
      .map(req => (req, (millis, req)))
    )

    val uz = b.add(Unzip[HttpRequest, (Long, HttpRequest)])
    val bc = b.add(Broadcast[HttpResponse](2))
    val zip = b.add(Zip[(Long, HttpRequest), HttpResponse])


    req ~> uz.in
    uz.out0 ~> hdl ~> bc
    bc.out(1) ~> zip.in1
    uz.out1                     ~> zip.in0
    zip.out ~> Sink.foreach(println) //TODO HTTP-Metric-Adapter

    FlowShape(req.in, bc.out(0))
  })

  val binding: Future[Http.ServerBinding] = Http(context.system).bind(interface = host, port = port).to(sink = Sink foreach { conn =>
    val remote = conn.remoteAddress

    conn.handleWith(handleRequest(remote, System.currentTimeMillis(), Route.handlerFlow(route)))
  })
  .run() pipeTo self

  override def receive: Receive = {
    case Http.ServerBinding(address) =>
      log.info("Service '{}' listen on {}", service.name, address)
      context.parent ! WatchService(self)

    case Failure(e) =>
      log.error(e, "Service '{}' Could not bind to {}:{} ... shutdown system", service.name, host, port)
      context.parent ! StopService
      context.stop(self)

    case StopService =>
      binding.flatMap(_.unbind()).onComplete {
        case util.Failure(e) =>
          log.error(e, "Error while close http-connection {}:{} for service '{}'", host, port, service.name)
          sender() ! ServiceStopped(self)
          context.stop(self)
        case _ =>
          sender() ! ServiceStopped(self)
          context.stop(self)
      }
  }

  override def serviceReceive: Receive = Actor.emptyBehavior

}