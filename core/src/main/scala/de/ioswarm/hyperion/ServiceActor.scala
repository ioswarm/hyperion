package de.ioswarm.hyperion

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.query.scaladsl.ReadJournal
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches}
import akka.stream.scaladsl.Keep
import com.typesafe.config.Config
import de.ioswarm.hyperion.Hyperion.{Stop, Stopped}
import de.ioswarm.hyperion.model.{Command, Event}

import scala.concurrent.Future

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

private[hyperion] class PropsForwardServiceActor(props: Props) extends ServiceActor {

  val ref: ActorRef = context.actorOf(props, self.path.name)

  override def registerSelf(): Unit = {
    register(self)
    register(ref)
  }

  def serviceReceive: Receive = {
    case a: Any => ref forward a
  }

}

private[hyperion] class ActorRefForwardServiceActor(ref: ActorRef) extends ServiceActor {

  def serviceReceive: Receive = {
    case a: Any => ref forward a
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
    //val ref = context.actorOf(service.props, service.name)
//    val ref = service.createActor
    val ref = serviceContext.actorOf(service)
    log.debug("Start child-service {} at {}", service.name, ref.path)
    if (service.initialize) {
      implicit val timeout: Timeout = Timeout(10.seconds) // TODO configure service-start-timeout
      ref ? Initialize map {
        case Initialized(xref) =>
          log.debug("Child-service {} at {} started", service.name, xref.path)
          childServiceMap += xref -> service
          ServiceStarted(service, xref)
      }
    } else {
      childServiceMap += ref -> service
      Future.successful(ServiceStarted(service, ref))
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

class PersistentServiceActor[T](service: PersistentService[T]) extends PersistentActor with ActorLogging {

  import Hyperion._

  implicit lazy val mat: ActorMaterializer = ActorMaterializer()

  implicit val serviceContext: ServiceContext = new ServiceContext(context, log)
  val snapshotInterval: Int = service.snapshotInterval
  var value: Option[T] = service.value

  context.setReceiveTimeout(service.timeout)

  var stream: Option[KillSwitch] = None

  def canRegister: Boolean = !service.sharded
  def register(ref: ActorRef): Unit = if (canRegister) context.actorSelection(context.system / "hyperion") ! Register(ref)
  def registerSelf(): Unit = register(self)

  def receiveEvent(value: Option[T], evt: Event): Option[T] = {
    val x = service.eventReceive(serviceContext)(value)(evt)
    log.debug("Receive-Event {} for {} - old-value: {} - new-value: {}", evt, persistenceId, value, x)
    x
  }

  /*def receiveEvent(evt: Event): Unit = {
    val x = service.eventReceive(serviceContext)(value)(evt)
    log.debug("Receive-Event {} for {} - old-value: {} - new-value: {}", evt, persistenceId, value, x)
    value = x
  }*/

  override def persistenceId: String = self.path.name

  override def receiveRecover: Receive = {
    case evt: Event =>
      value = receiveEvent(value, evt)
      /*receiveEvent(evt)*/
    case SnapshotOffer(_, snapshot: Any) =>
      log.debug("Recover snapshot: {}", snapshot)
      value = Some(snapshot.asInstanceOf[T])
    case RecoveryCompleted =>
      stream = service.queryStream.map(q => q.run(persistenceId, lastSequenceNr+1l, Long.MaxValue))
  }

  override def receiveCommand: Receive = {
    case cmd: Command =>
      val repl = sender()
      log.debug("Receive command: {}", cmd)
      val action = service.commandReceive(serviceContext)(value)(cmd)
      log.debug("Got Action {}", action)
      if (action.isPersistable) {
        persist(action.taggedValue) { evt =>
          log.debug("TAGGED: " + evt)
          value = receiveEvent(value, evt.payload.asInstanceOf[Event])
          /*receiveEvent(evt.payload.asInstanceOf[Event])*/
          if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0 && value.isDefined)
            saveSnapshot(value.get)
          if (action.isReplyable) repl ! evt.payload.asInstanceOf[Event]
        }
      } else if (action.isReplyable) repl ! action.value

      /*val fut: Future[Action[Event]] =  service.commandReceive(serviceContext)(value)(cmd)
      val repl = sender()
      fut onComplete {
        case Success(action) =>
          if (action.isPersistable) {
            persist(action.taggedValue) { evt =>
              log.debug("TAGGED: " + evt)
              value = receiveEvent(value, evt.payload.asInstanceOf[Event])
              if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0 && value.isDefined)
                saveSnapshot(value.get)
              if (action.isReplyable) repl ! evt.payload.asInstanceOf[Event]
            }
          } else if (action.isReplyable) repl ! action.value
        case Failure(e) =>
          receiveEvent(value, FailureEvent(e))
          //repl ! akka.actor.Status.Failure(e)
      }*/
    case ReceiveTimeout =>
      log.debug("Receive timeout ... passivate persistenceId: "+persistenceId)
      if (service.sharded) context.parent ! Passivate(stopMessage = Stop)
      else self ! Stop
    case Initialize =>
      log.debug("Initialize persistent-service {} at {}", self.path.name, self.path)
      val repl = sender()
      log.debug("Persistent-service {} at {} initialized.", self.path.name, self.path)
      registerSelf()
      repl ! Initialized(self)
    case Stop =>
      log.debug("Persistent-service {} at {} shutting down...", self.path.name, self.path)
      val repl = sender()
      stream.foreach(_.shutdown())
      context.stop(self)
      log.debug("Persistent-service {} at {} is down.", self.path.name, self.path)
      if (!service.sharded) repl ! Stopped(self)

  }

}

class PersistenceQueryServiceActor[T <: ReadJournal](service: PersistenceQueryService[T], fromSequenceNr: Long, toSequenceNr: Long) extends ServiceActor {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  val (killSwitch, last) = service.source(context.system, fromSequenceNr, toSequenceNr)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(service.sink)(Keep.both)
    .run()

  override def serviceReceive: Receive = {
    case Stop =>
      log.debug("Service {} at {} shutting down...", self.path.name, self.path)
      val repl = sender()
      killSwitch.shutdown()
      context.stop(self)
      log.debug("Service {} at {} is down.", self.path.name, self.path)
      repl ! Stopped(self)
  }

  override def receive: Receive = serviceReceive orElse actorReceive

}

class PipeServiceActor[Mat](service: PipeService[Mat]) extends ServiceActor {

  import akka.pattern.pipe
  import context.dispatcher

  val futMat: Future[Mat] = service.pipe(serviceContext) pipeTo self

  override def serviceReceive: Receive = service.receive(serviceContext)

}

// --- STREAMING ---
class StreamServiceActor[Mat](service: StreamingService[Mat]) extends PipeServiceActor[Mat](service) {

  

}
