package de.ioswarm.hyperion

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

object Hyperion {
  def apply(implicit system: ActorSystem): Hyperion = new HyperionImpl(system)
  def apply(name: String): Hyperion = new HyperionImpl(ActorSystem(name))

  private lazy val _config: Config = ConfigFactory.load().getConfig("hyperion")
  def config: Config = _config

  case object Initialize
  final case class Initialized(ref: ActorRef)

  final case class Register(ref: ActorRef)

  final case class StartService(service: Service)
  final case class ServiceStarted(service: Service, ref: ActorRef)

  case object Stop
  final case class Stopped(ref: ActorRef)

  case object Shutdown

}
trait Hyperion {

  def name: String = system.name
  def system: ActorSystem

  def config: Config = system.settings.config.getConfig("hyperion")
  def log: LoggingAdapter = system.log

  def run(service: Service): Future[ActorRef]
  def start(services: Service*): Future[ActorRef]
  def stop(): Future[Done]

  def terminate(): Future[Terminated] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    stop().flatMap {
      case Done => system.terminate()
    }
  }

  def whenTerminated: Future[Terminated] = system.whenTerminated

}
private[hyperion] class HyperionImpl(val system: ActorSystem) extends Hyperion {

  import akka.pattern.ask
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import Hyperion._

  private var hyperionActor: Option[ActorRef] = None

  private def hyperionService(services: Service*) = ActorServiceImpl(name = "hyperion", children = System(config) +: Management(config) +: services)

  override def run(service: Service): Future[ActorRef] = {
    implicit val timeout: Timeout = Timeout(10.seconds)  // TODO configure service-startup-timeout

    hyperionActor match {
      case Some(ref) => for {
        res <- ref ? StartService(service)
      } yield res match {
        case ServiceStarted(_, xref) => xref
      }
      case None => start(service)
    }
  }

  override def start(services: Service*): Future[ActorRef] = hyperionActor match {
    case Some(ref) =>
      log.warning("Hyperion already started.")
      Future.successful(ref)
    case None =>
      hyperionActor = Some(system.actorOf(Props(classOf[HyperionActor], hyperionService(services :_*)), "hyperion"))
      implicit val timeout: Timeout = Timeout(60.seconds)  // TODO configure system-startup-timeout
      hyperionActor match {
        case Some(ref) => for {
          res <- ref ? Initialize
        } yield res match {
          case Initialized(rref) => rref
        }
        case _ => Future.failed(new Exception("Error while initialize Services."))
      }
  }

  override def stop(): Future[Done] = {
    implicit val timeout: Timeout = Timeout(60.seconds)  // TODO configure service-stop-timeout

    hyperionActor match {
      case Some(ref) => for {
        res <- ref ? Stop
      } yield res match {
        case Stopped(_) =>
          hyperionActor = None
          Done
        case a: Any =>
          println(s"STOP RECEIVED: $a")
          Done
      }
      case None => Future.successful(Done)
    }
  }

}