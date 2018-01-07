package de.ioswarm.hyperion.management

import akka.actor.{ActorPath, ActorSelection, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration._
import de.ioswarm.hyperion.{ManagerService, ServiceActor}
import de.ioswarm.hyperion.Hyperion._
import de.ioswarm.hyperion.model.{HttpMetricEvent, LogEvent, MetricEvent}

object HyperionManager {

  case class GetLogs(logType: String)
  case class Logs(logs: Vector[LogEvent])

  case object GetMetrics
  case class Metrics(metrics: Vector[MetricEvent])

  case object GetRequestMetrics
  case class RequestMetrics(metrics: Vector[HttpMetricEvent])

}
class HyperionManager() extends ManagerService {

  override def config: Config = super.config.getConfig("management.hyperion-manager")

  def responseTimeout: FiniteDuration = Some(Duration(config.getString("response-timeout"))).collect {
    case f: FiniteDuration => f
  }.getOrElse(10.seconds)



  override def route: ServiceRoute = { ref =>
    import HyperionManager._
    import akka.http.scaladsl.model.StatusCodes._
    import akka.http.scaladsl.server.Directives._
    import de.heikoseeberger.akkahttpargonaut.ArgonautSupport._

    implicit val timeout: Timeout = Timeout(responseTimeout)

    pathPrefix("api" / "v1") {
      pathPrefix("shutdown") {
        post {
         ref ! Shutdown
          complete(OK)
        }
      } ~
      pathPrefix("heartbeat") {
        pathEnd {
          get {
            complete(OK)
          }
        }
      } ~
      pathPrefix("logs") {
        get {
          pathPrefix(Segment) { s =>
            onSuccess(ref ? GetLogs(s.toUpperCase)) {
              case Logs(logs) => complete(logs)
              case _ => complete(NotFound)
            }
          } ~
          pathEnd {
            onSuccess(ref ? GetLogs("*")) {
              case Logs(logs) => complete(logs)
              case _ => complete(NotFound)
            }
          }
        }
      } ~
      pathPrefix("metrics") {
        pathPrefix("system") {
          get {
            pathEnd {
              onSuccess(ref ? GetMetrics) {
                case Metrics(metrics) => complete(metrics)
                case _ => complete(NotFound)
              }
            }
          }
        } ~
        pathPrefix("request") {
          get {
            pathEnd {
              onSuccess(ref ? GetRequestMetrics) {
                case RequestMetrics(metrics) => complete(metrics)
                case _ => complete(NotFound)
              }
            }
          }
        }
      }
    }
  }

  override def name: String = "hyperion"

  override def props: Props = Props[HyperionManagementService]

}

final class HyperionManagementService extends ServiceActor {

  import scala.collection.mutable
  import HyperionManager._
  import de.ioswarm.hyperion.util.FiniteQueue._

  override def config: Config = super.config.getConfig("hyperion.management.hyperion-manager")

  val logMax: Int = config.getInt("max-logs")
  val metricMax: Int = config.getInt("max-metrics")
  val requestMax: Int = config.getInt("max-request-metrics")

  val hyPath: ActorPath = context.system / "hyperion"
  val hyActor: ActorSelection = context.actorSelection(hyPath)

  var logs: mutable.Queue[LogEvent] = mutable.Queue.empty[LogEvent]
  val metrics: mutable.Queue[MetricEvent] = mutable.Queue.empty[MetricEvent]
  val requests: mutable.Queue[HttpMetricEvent] = mutable.Queue.empty[HttpMetricEvent]

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[LogEvent])
    context.system.eventStream.subscribe(self, classOf[MetricEvent])
    context.system.eventStream.subscribe(self, classOf[HttpMetricEvent])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  def serviceReceive: Receive = {
    case Shutdown => hyActor ! Shutdown

    case GetLogs(mod) if mod == "*" =>
      val repl = sender()
      repl ! Logs(logs.toVector.reverse)
    case GetLogs(mod) =>
      val repl = sender()
      repl ! Logs(logs.filter(e => e.logType == mod).toVector.reverse)

    case GetMetrics =>
      val repl = sender()
      repl ! Metrics(metrics.toVector.reverse)

    case GetRequestMetrics =>
      val repl = sender()
      repl ! RequestMetrics(requests.toVector.reverse)

    case e: LogEvent => logs.enqueueFinite(e, logMax)
    case e: MetricEvent => metrics.enqueueFinite(e, metricMax)
    case e: HttpMetricEvent => requests.enqueueFinite(e, requestMax)
  }

}