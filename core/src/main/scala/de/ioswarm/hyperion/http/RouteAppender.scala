package de.ioswarm.hyperion.http

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.Hyperion

object RouteAppender {

  final case class AppendRoute(route: Route)
  final case class AppendMiddleware(middleware: Middleware)

  sealed trait ServerState
  private[http] case object Down extends ServerState
  private[http] case object Up extends ServerState
  private[http] case object Initialize extends ServerState

  sealed trait RouteData
  private[http] case object NoRoute extends RouteData
  private[http] final case class Routes(route: Option[Route], middleware: Option[Middleware]) extends RouteData {
    import akka.http.scaladsl.server.Directives._

    def isDefined: Boolean = route.isDefined

    def appendRoute(rt: Route): Routes = copy(route = this.route match {
      case Some(r) => Some(r ~ rt)
      case None => Some(rt)
    })

    def appendMiddleware(mid: Middleware): Routes = copy(middleware = this.middleware match {
      case Some(m) => Some(m andThen mid)
      case None => Some(mid)
    })

    def buildRoute: Option[Route] = route.map{ rt => middleware match {
      case Some(mid) => mid(rt)
      case None => rt
    }}
  }

}
class RouteAppender(hostname: String, port: Int, clazz: Class[_]) extends FSM[RouteAppender.ServerState, RouteAppender.RouteData] with ActorLogging {

  import concurrent.duration._

  def startupService(route: Route): ActorRef = context.actorOf(Props(clazz, hostname, port, route), "http")

  def stopService(): Unit = context.child("http") match {
    case Some(ref) => ref ! Hyperion.Stop
    case None =>
  }

  startWith(RouteAppender.Down, RouteAppender.NoRoute)

  when(RouteAppender.Down) {
    case Event(RouteAppender.AppendRoute(route), RouteAppender.NoRoute) =>
      log.debug("AppendRoute called with NoRoute <- in DOWN")
      goto(RouteAppender.Initialize) using RouteAppender.Routes(Some(route), None)

    case Event(RouteAppender.AppendRoute(route), RouteAppender.Routes(None, Some(middleware))) =>
      log.debug("AppendRoute called with Middleware <- in DOWN")
      goto(RouteAppender.Initialize) using RouteAppender.Routes(Some(route), Some(middleware))

    case Event(RouteAppender.AppendMiddleware(middleware), RouteAppender.NoRoute) =>
      log.debug("AppendMiddleware with NoRoute called <- in DOWN")
      stay using RouteAppender.Routes(None, Some(middleware))

    case Event(RouteAppender.AppendMiddleware(middleware), routes: RouteAppender.Routes) =>
      log.debug("AppendMiddleware with Middleware called <- in DOWN")
      stay using routes.appendMiddleware(middleware)
  }

  when(RouteAppender.Initialize, stateTimeout = 2.seconds /* TODO configure */) {
    case Event(RouteAppender.AppendRoute(route), routes: RouteAppender.Routes) =>
      log.debug("AppendRoute called <- in INITIALIZE")
      stay using routes.appendRoute(route)

    case Event(RouteAppender.AppendMiddleware(middleware), routes: RouteAppender.Routes) =>
      log.debug("AppendMiddleware called <- in INITIALIZE")
      stay using routes.appendMiddleware(middleware)

    case Event(StateTimeout, routes: RouteAppender.Routes) =>
      log.debug("StateTimeout called <- in INITIALIZE")
      goto(RouteAppender.Up) using routes
  }

  when(RouteAppender.Up) {
    case Event(RouteAppender.AppendRoute(route), routes: RouteAppender.Routes) =>
      log.debug("AppendRoute called <- in UP")
      goto(RouteAppender.Initialize) using routes.appendRoute(route)
    case Event(RouteAppender.AppendMiddleware(middleware), routes: RouteAppender.Routes) =>
      log.debug("AppendMiddleware called <- in UP")
      goto(RouteAppender.Initialize) using routes.appendMiddleware(middleware)
  }

  onTransition {
    case RouteAppender.Down -> RouteAppender.Initialize =>
      log.debug("switch from DOWN to INITIALIZE")

    case RouteAppender.Initialize -> RouteAppender.Up =>
      log.debug("switch from INITIALIZE to UP")
      stateData match {
        case routes: RouteAppender.Routes => routes.buildRoute match {
          case Some(rt) => startupService(rt)
          case None => log.info("There is no route")
        }
        case _ => log.warning("stateData is not defined")
      }

    case RouteAppender.Up -> RouteAppender.Initialize =>
      log.debug("switch from UP to INITIALIZE")
      stopService()
  }

  initialize()

}
