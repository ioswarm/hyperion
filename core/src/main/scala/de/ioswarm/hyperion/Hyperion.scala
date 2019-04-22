package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import de.ioswarm.hyperion.Hyperion.Settings
import de.ioswarm.hyperion.http.RouteAppender

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

object Hyperion {

  case object Stop

  final case class HttpAppendRoute(route: Route)

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

    lazy val hyperionConfig: Config = config.getConfig("hyperion")

    def systemName: String = hyperionConfig.getString("name")

    def baseActorClassName: String = hyperionConfig.getString("internal.baseActor")
    def httpActorClassName: String = hyperionConfig.getString("internal.httpActor")

    def httpHost: String = hyperionConfig.getString("http.host")
    def httpPort: Int = hyperionConfig.getInt("http.port")

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
}


private[hyperion] class HyperionActor(settings: Settings) extends Actor with ActorLogging {

  import Hyperion._

  val routeAppender: ActorRef = context.actorOf(Props(
      classOf[RouteAppender]
      , settings.httpHost
      , settings.httpPort
      , Class.forName(settings.httpActorClassName)
    ), "routes")

  def receive: Receive = {
    case HttpAppendRoute(route) => routeAppender ! RouteAppender.AppendRoute(route)
    case _ =>
  }

}
