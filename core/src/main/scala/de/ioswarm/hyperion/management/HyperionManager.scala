package de.ioswarm.hyperion.management

import akka.actor.{ActorPath, ActorSelection, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import de.ioswarm.hyperion.{ManagerService, ServiceActor}
import de.ioswarm.hyperion.Hyperion._
import de.ioswarm.hyperion.model.LogEvent

object HyperionManager {

  case class GetLogs(logType: String)
  case class Logs(logs: Vector[LogEvent])

}
class HyperionManager() extends ManagerService {

  override def route: ServiceRoute = { ref =>
    import HyperionManager._
    import akka.http.scaladsl.model.StatusCodes._
    import akka.http.scaladsl.server.Directives._
    import de.heikoseeberger.akkahttpargonaut.ArgonautSupport._

    implicit val timeout: Timeout = Timeout(10.seconds)  // TODO configure response-timeout

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
            }
          } ~
          pathEnd {
            onSuccess(ref ? GetLogs("*")) {
              case Logs(logs) => complete(logs)
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

  val logMax = 1000 // TODO configure log-max-entries
  val hyPath: ActorPath = context.system / "hyperion"
  val hyActor: ActorSelection = context.actorSelection(hyPath)
  var logs: mutable.Queue[LogEvent] = mutable.Queue.empty[LogEvent]

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[LogEvent])
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

    case e: LogEvent => logs.enqueueFinite(e, logMax)
  }

}