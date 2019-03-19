package de.ioswarm.hyperion.http

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.Hyperion

object RouteAppender {

  final case class AppendRoute(route: Route)

  sealed trait ServerState
  case object Down extends ServerState
  case object Up extends ServerState
  case object Initialize extends ServerState

  sealed trait RouteData
  case object NoRoute extends RouteData
  final case class Routes(route: Option[Route]) extends RouteData {
    import akka.http.scaladsl.server.Directives._

    def isDefined: Boolean = route.isDefined

    def append(rt: Route): Routes = copy(route = this.route match {
      case Some(r) => Some(r ~ rt)
      case None => Some(rt)
    })

  }

}
class RouteAppender(hostname: String, port: Int, clazz: Class[_]) extends FSM[RouteAppender.ServerState, RouteAppender.RouteData] with ActorLogging {

  import RouteAppender._
  import concurrent.duration._

  def startupService(route: Route): ActorRef = context.actorOf(Props(clazz, hostname, port, route), "http")

  def stopService(): Unit = context.child("http") match {
    case Some(ref) => ref ! Hyperion.Stop
    case None =>
  }

  startWith(Down, NoRoute)

  when(Down) {
    case Event(AppendRoute(route), NoRoute) =>
      log.debug("AppendRoute called <- in DOWN for {}", route)
      goto(Initialize) using Routes(Some(route))
  }

  when(Initialize, stateTimeout = 2.seconds /* TODO configure */) {
    case Event(AppendRoute(route), routes: Routes) =>
      log.debug("AppendRoute called <- in INITIALIZE")
      stay using routes.append(route)

    case Event(StateTimeout, routes: Routes) =>
      log.debug("StateTimeout called <- in INITIALIZE")
      goto(Up) using routes
  }

  when(Up) {
    case Event(AppendRoute(route), routes: Routes) =>
      log.debug("AppendRoute called <- in UP")
      goto(Initialize) using routes.append(route)
  }

  onTransition {
    case Down -> Initialize =>
      log.debug("switch from DOWN to INITIALIZE")

    case Initialize -> Up =>
      log.debug("switch from INITIALIZE to UP")
      stateData match {
        case Routes(route) => route match {
          case Some(rt) => startupService(rt)
          case None => log.info("There is no route")
        }
        case _ => log.warning("stateData is not defined")
      }

    case Up -> Initialize =>
      log.debug("switch from UP to INITIALIZE")
      stopService()
  }

  initialize()

}
