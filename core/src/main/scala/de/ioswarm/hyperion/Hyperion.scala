package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import de.ioswarm.hyperion.Hyperion.{HttpAppendMiddleware, Settings}
import de.ioswarm.hyperion.http.{Middleware, RouteAppender}
import de.ioswarm.hyperion.utils.Reflect

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

object Hyperion {

  import de.ioswarm.hyperion.http._

  case object Stop

  final case class HttpAppendRoute(route: Route)
  final case class HttpAppendMiddleware(middleware: Middleware)

  def apply(system: ActorSystem): Hyperion = apply(system, new Settings(system.settings.config))
  def apply(system: ActorSystem, settings: Settings): Hyperion = new HyperionImpl(system, settings)

  def apply(name: String): Hyperion = apply(ActorSystem(name))
  def apply(name: String, config: Config): Hyperion = apply(ActorSystem(name, config))

  def apply(): Hyperion = apply(ConfigFactory.load())
  def apply(config: Config): Hyperion = {
    val settings = new Settings(config)
    apply(ActorSystem(settings.systemName, settings.config), settings)
  }


  class Settings(val config: Config) {
    import collection.JavaConverters._

    lazy val hyperionConfig: Config = config.getConfig("hyperion")

    def systemName: String = hyperionConfig.getString("name")

    def baseActorClassName: String = hyperionConfig.getString("internal.baseActor")
    def httpActorClassName: String = hyperionConfig.getString("internal.httpActor")

    private[hyperion] def internalMiddleware: List[String] = hyperionConfig.getStringList("internal.middleware").asScala.toList

    def httpHost: String = hyperionConfig.getString("http.host")
    def httpPort: Int = hyperionConfig.getInt("http.port")

    def middleware: List[String] = hyperionConfig.getStringList("http.middleware").asScala.toList
  }

}

abstract class Hyperion(val system: ActorSystem) extends AkkaProvider {

  def name: String = system.name

  implicit def log: LoggingAdapter = system.log

  implicit def dispatcher: ExecutionContextExecutor = system.dispatcher

  def actorOf(props: Props): ActorRef = system.actorOf(props)

  def actorOf(props: Props, name: String): ActorRef = system.actorOf(props, name)

  def config: Config = system.settings.config

  def stop(actor: ActorRef): Unit = system.stop(actor)

  def terminate(): Future[Terminated]

  def whenTerminated: Future[Terminated]

  def await(): Terminated = Await.result(whenTerminated, Duration.Inf)

  def middleware(middleware: Middleware): Unit

}

private[hyperion] class HyperionImpl(system: ActorSystem, val settings: Settings) extends Hyperion(system) {

  log.debug("Start hyperion-base-actor: {}", settings.baseActorClassName)

  val hyperionRef: ActorRef = system.actorOf(Props(
    Class.forName(settings.baseActorClassName)
    , settings
  ), "hyperion")

  override def terminate(): Future[Terminated] = system.terminate()

  override def whenTerminated: Future[Terminated] = system.whenTerminated

  override def self: ActorRef = hyperionRef

  override def sender(): ActorRef = self

  override def middleware(middleware: Middleware): Unit = hyperionRef ! HttpAppendMiddleware(middleware)
}


private[hyperion] class HyperionActor(settings: Settings) extends Actor with ActorLogging {

  import Hyperion._

  val routeAppender: ActorRef = context.actorOf(Props(
      classOf[RouteAppender]
      , settings.httpHost
      , settings.httpPort
      , Class.forName(settings.httpActorClassName)
    ), "routes")

  context.watch(routeAppender)

  def loadMiddleware(paths: List[String]): Unit = paths.foreach{ pth =>
      try {
        log.debug("\t{}", pth.replace("#", " -> "))
        routeAppender ! RouteAppender.AppendMiddleware(Reflect.execPath[Middleware](pth))
      } catch {
        case e: Exception => log.error(e, "Could not create an instance of {}", pth)
      }
    }

  log.debug("Attach internal middleware")
  loadMiddleware(settings.internalMiddleware)

  log.debug("Attach middleware")
  loadMiddleware(settings.middleware)

  def receive: Receive = {
    case HttpAppendRoute(route) => routeAppender ! RouteAppender.AppendRoute(route)
    case HttpAppendMiddleware(middleware) => routeAppender ! RouteAppender.AppendMiddleware(middleware)
    case _ =>
  }

}
