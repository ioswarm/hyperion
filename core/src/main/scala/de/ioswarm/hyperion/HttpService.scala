package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.Status.Failure
import akka.pattern.pipe
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import de.ioswarm.hyperion.ServiceCommands.Stop

import scala.concurrent.Future
import scala.collection.immutable

/**
  * Created by andreas on 05.11.17.
  */
object HttpService {

  def apply(host: String, port: Int, route: Route): Service[NotUsed] = ServiceImpl(
    s"http_${host}_$port"
    , Service.emptyBehavior
    , Service.emptyRoute
    , classOf[HttpService]
    , immutable.Seq(host, port, route)
    , immutable.Seq.empty[Service[_]]
  )

}
final class HttpService(service: Service[NotUsed], host: String, port: Int, route: Route) extends ServiceActor(service) {

  import context.dispatcher

  implicit val mat = ActorMaterializer()

  val binding: Future[Http.ServerBinding] = Http(context.system).bindAndHandle(route, host, port)
  binding pipeTo self

  override def receive: Receive = {
    case Http.ServerBinding(address) =>
      log.info("listen on {}", address)

    case Failure(e) =>
      log.error(e, "Could not bind to {}:{}", host, port)
      context.stop(self)

    case Stop =>
      binding.flatMap(_.unbind()).onComplete {
        case util.Failure(e) =>
          log.error(e, "Error while close http-connection {}:{}", host, port)
          context.stop(self)
        case _ =>
          context.stop(self)
      }
  }

}
