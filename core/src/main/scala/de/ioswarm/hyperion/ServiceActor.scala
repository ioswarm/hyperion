package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, PoisonPill, Props, Terminated}
import com.typesafe.config.Config

trait ServiceActor extends Actor with ActorLogging {

  import de.ioswarm.hyperion.Hyperion._

  implicit val serviceContext: ServiceContext = new ServiceContext(context, log)

  def config: Config = serviceContext.config

  def canRegister: Boolean = true
  def register(ref: ActorRef): Unit = if (canRegister) context.actorSelection(context.system / "hyperion") ! Register(ref)
  def registerSelf(): Unit = register(self)

  def serviceReceive: Receive

  def actorReceive: Receive = {
    case Initialize =>
      log.debug("Initialize service {} at {}", self.path.name, self.path)
      val repl = sender()
      log.debug("Service {} at {} initialized.", self.path.name, self.path)
      registerSelf()
      repl ! Initialized(self)
    case Stop =>
      log.debug("Service {} at {} shutting down...", self.path.name, self.path)
      val repl = sender()
      context.stop(self)
      log.debug("Service {} at {} is down.", self.path.name, self.path)
      repl ! Stopped(self)
  }

  def receive: Receive = actorReceive orElse serviceReceive

}

private[hyperion] class ForwardServiceActor(props: Props) extends ServiceActor {

  val actorRef: ActorRef = context.actorOf(props)

  override def registerSelf(): Unit = {
    register(self)
    register(actorRef)
  }

  def serviceReceive: Receive = {
    case a: Any => actorRef forward a
  }

}

class ActorServiceActor(service: ActorService) extends ServiceActor {

  import context.dispatcher
  import akka.actor.{ActorPath, ActorRef, PoisonPill}
  import akka.pattern.ask
  import akka.util.Timeout
  import java.util.concurrent.ConcurrentHashMap
  import scala.concurrent.Future
  import scala.concurrent.duration._
  import scala.collection.concurrent
  import scala.collection.JavaConverters._
  import scala.util.{Failure, Success}
  import Hyperion._

  val childServiceMap: concurrent.Map[ActorRef, Service] = new ConcurrentHashMap[ActorRef, Service]().asScala
  def childPaths: List[ActorPath] = childServiceMap.keySet.map(ref => ref.path).toList

  def startChildService(service: Service): Future[ServiceStarted] = {
    val ref = context.actorOf(service.props, service.name)
    log.debug("Start child-service {} at {}", service.name, ref.path)
    implicit val timeout: Timeout = Timeout(10.seconds)  // TODO configure service-start-timeout
    ref ? Initialize map {
      case Initialized(xref) =>
        log.debug("Child-service {} at {} started", service.name, xref.path)
        childServiceMap += xref -> service
        ServiceStarted(service, xref)
    }
  }

  def stopService()(implicit timeout: Timeout): Future[Stopped] = {
    context.children.filter(ref => !childPaths.contains(ref.path)).foreach{ ref =>
      log.debug("Stop unknown actor {} with PoisonPill.", ref.path)
      ref ! PoisonPill
    }
    Future.sequence(context.children.filter(ref => childPaths.contains(ref.path)).map{ ref =>
      stopChildService(ref)
    }).map{ _ =>
      Stopped(self)
    }
  }

  def stopChildService(ref: ActorRef)(implicit timeout: Timeout): Future[Stopped] = {
    log.debug("Send stop-event to child-service at {}", ref.path)
    for {
      s <- (ref ? Stop).mapTo[Stopped]
    } yield {
      log.debug("Child-service at {} stopped", ref.path)
      s
    }
  }

  override def actorReceive: Receive = {
    case Initialize =>
      val repl = context.sender()
      log.debug("Initialize service {} at {}", service.name, self.path)
      Future sequence service.children.map(c => startChildService(c)) onComplete {
        case Success(_) =>
          log.debug("Service {} at {} initialized.", service.name, self.path)
          register(self)
          repl ! Initialized(self)
        case Failure(e) =>
          log.error(e, "Error while starting child-services of {} at {}", service.name, self.path)
          repl ! akka.actor.Status.Failure(e)
      }

    case StartService(srv) =>
      val repl = sender()
      startChildService(srv) onComplete {
        case Success(s: ServiceStarted) => repl ! s
        case Failure(e) => repl ! akka.actor.Status.Failure(e)
      }

    case Stop =>
      val repl = sender()
      log.debug("Service {} at {} shutting down...", service.name, self.path)
      implicit val timeout: Timeout = Timeout(10.seconds)  // TODO configure stop-timeout
      stopService() onComplete {
        case Success(_) =>
          log.debug("Service {} at {} is down.", service.name, self.path)
          repl ! Stopped(self)
          context.stop(self)
        case Failure(e) =>
          log.error(e, "While stop service {} at {}", service.name, self.path)
          repl ! akka.actor.Status.Failure(e)
      }

  }

  def serviceReceive: Receive = service.receive(serviceContext)

}
