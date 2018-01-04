package de.ioswarm.hyperion

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.http.scaladsl.server.Route
import akka.routing.RouterConfig
import de.ioswarm.hyperion.Service.{CommandReceive, EventReceive}
import de.ioswarm.hyperion.model.{Action, Command, Event}

import scala.concurrent.duration.Duration

object Service {
  type ServiceReceive = ServiceContext => Actor.Receive
  type ServiceRoute = ActorRef => Route

  type CommandReceive[T] = Option[T] => PartialFunction[Command, Action[Event]]
  type EventReceive[T] = Option[T] => PartialFunction[Event, T]

  val defaultIdExtractor: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id, cmd)
  }
  val defaultShardResolver: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.id.hashCode) % 100).toString
  }

  object emptyBehavior extends ServiceReceive {
    override def apply(ctx: ServiceContext): Actor.Receive = Actor.emptyBehavior
  }

  object emptyRoute extends ServiceRoute {
    import akka.http.scaladsl.server.Directives._
    override def apply(ref: ActorRef): Route = reject
  }

}
trait Service {

  def name: String
  def props: Props

}

case class ForwardServiceImpl(name: String, actorProps: Props) extends Service {

  def props: Props = Props(classOf[ForwardServiceActor], actorProps)

}

trait ActorService extends Service {

  type ServiceReceive = Service.ServiceReceive

  def receive: ServiceReceive
  def dispatcher: Option[String]
  def router: Option[RouterConfig]

  def actorClass: Class[_ <: ActorServiceActor]
  def actorArgs: Seq[Any]

  def children: Seq[Service]

  def hasReceive: Boolean = receive != Service.emptyBehavior

  def withReceive[T >: ActorService](receive: ServiceReceive): T
  def withDispatcher[T >: ActorService](d: String): T
  def withRouter[T >: ActorService](r: RouterConfig): T

  def withActor[T >: ActorService](clazz: Class[_ <: ActorServiceActor]): T
  def withArgs[T >: ActorService](args: Any*): T

  def :=[T >: ActorService](children: Seq[Service]): T
  def +=[T >: ActorService](child: Service): T
  def ++=[T >: ActorService](children: Seq[Service]): T

  def withServices[T >: ActorService](children: Seq[Service]): T = :=(children)
  def appendService[T >: ActorService](child: Service): T = +=(child)
  def appendServices[T >: ActorService](children: Seq[Service]): T = ++=(children)

}

case class ActorServiceImpl(
                           name: String
                           , receive: Service.ServiceReceive = Service.emptyBehavior
                           , dispatcher: Option[String] = None
                           , router: Option[RouterConfig] = None
                           , actorClass: Class[_ <: ActorServiceActor] = classOf[ActorServiceActor]
                           , actorArgs: Seq[Any] = Seq.empty[Any]
                           , children: Seq[Service] = Seq.empty[Service]
                           ) extends ActorService {

  def props: Props = {
    var p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p = p.withDispatcher(dispatcher.get)
    if (router.isDefined) p = p.withRouter(router.get)
    p.copy()
  }

  def withReceive[T >:ActorService](r: ServiceReceive): T = copy(receive = r)
  def withDispatcher[T >: ActorService](d: String): T = copy(dispatcher = Some(d))
  def withRouter[T >: ActorService](r: RouterConfig): T = copy(router = Some(r))

  def withActor[T >: ActorService](clazz: Class[_ <: ActorServiceActor]): T = copy(actorClass = clazz)
  def withArgs[T >: ActorService](args: Any*): T = copy(actorArgs = args)

  def :=[T >: ActorService](s: Seq[Service]): T = copy(children = s)
  def +=[T >: ActorService](s: Service): T = copy(children = s +: this.children)
  def ++=[T >: ActorService](s: Seq[Service]): T = copy(children = this.children ++ s)

}

trait HttpService extends ActorService {
  type ServiceRoute = Service.ServiceRoute

  def route: ServiceRoute

  def hasRoute: Boolean = route != Service.emptyRoute

  def withRoute[T >: HttpService](route: ServiceRoute): T
}

case class HttpServiceImpl(
                            name: String
                            , receive: Service.ServiceReceive = Service.emptyBehavior
                            , route: Service.ServiceRoute = Service.emptyRoute
                            , dispatcher: Option[String] = None
                            , router: Option[RouterConfig] = None
                            , actorClass: Class[_ <: ActorServiceActor] = classOf[ActorServiceActor]
                            , actorArgs: Seq[Any] = Seq.empty[Any]
                            , children: Seq[Service] = Seq.empty[Service]
                          ) extends HttpService {

  def props: Props = {
    var p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p = p.withDispatcher(dispatcher.get)
    if (router.isDefined) p = p.withRouter(router.get)
    p.copy()
  }

  def withReceive[T >: HttpService](r: ServiceReceive): T = copy(receive = r)
  def withDispatcher[T >: HttpService](d: String): T = copy(dispatcher = Some(d))
  def withRouter[T >: HttpService](r: RouterConfig): T = copy(router = Some(r))

  def withActor[T >: HttpService](clazz: Class[_ <: ActorServiceActor]): T = copy(actorClass = clazz)
  def withArgs[T >: HttpService](args: Any*): T = copy(actorArgs = args)

  def :=[T >: HttpService](s: Seq[Service]): T = copy(children = s)
  def +=[T >: HttpService](s: Service): T = copy(children = s +: this.children)
  def ++=[T >: HttpService](s: Seq[Service]): T = copy(children = this.children ++ s)

  def withRoute[T >: HttpService](r: ServiceRoute): T = copy(route = r)

}

trait ManagerService extends Service {
  type ServiceRoute = Service.ServiceRoute

  def route: ServiceRoute

}

// TODO implement singleton-proxy-service
case class SingletonServiceImpl[T <:Service](service: T)(implicit hy: Hyperion) extends Service {
  import Hyperion._

  override def name: String = service.name

  override def props: Props = ClusterSingletonManager.props(
    singletonProps = service.props
    , terminationMessage = Stop
    , settings = ClusterSingletonManagerSettings(hy.system)
  )

}

trait PersistentService[T] extends Service {
  import Service._

  def value: Option[T]
  def commands: List[CommandReceive[T]]
  def events: List[EventReceive[T]]
  def timeout: Duration
  def snapshotInterval: Int
  def dispatcher: Option[String]
  def actorClass: Class[_ >: PersistentServiceActor[T]]
  def actorArgs: Seq[Any]
  def sharded: Boolean

  def withValue(t: Option[T]): PersistentService[T]
  def withTimeout(d: Duration): PersistentService[T]
  def withSnapshotInterval(i: Int): PersistentService[T]
  def withDispatcher(d: String): PersistentService[T]
  def withActor(c: Class[_ >: PersistentServiceActor[T]]): PersistentService[T]
  def withArgs(args: Any*): PersistentService[T]
  def withSharding(b: Boolean): PersistentService[T]

  def command(c: CommandReceive[T]): PersistentService[T]
  def event(e: EventReceive[T]): PersistentService[T]

  def commandReceive(value: Option[T]): PartialFunction[Command, Action[Event]] = {
    require(commands.nonEmpty)
    val first = commands.head
    commands.aggregate(first(value))( { (p, cmd) => p orElse cmd(value) }, { (p1, p2) => p1 orElse p2 })
  }

  def eventReceive(value: Option[T]): PartialFunction[Event, T] = {
    require(events.nonEmpty)
    val first = events.head
    events.aggregate(first(value))({(p, evt) => p orElse evt(value)}, {(p1, p2) => p1 orElse p2})
  }

}
case class PersistentServiceImpl[T](
                                   name: String
                                   , value: Option[T] = None
                                   , commands: List[CommandReceive[T]] = List.empty[CommandReceive[T]]
                                   , events: List[EventReceive[T]] = List.empty[EventReceive[T]]
                                   , timeout: Duration = Duration.Inf
                                   , snapshotInterval: Int = Int.MaxValue
                                   , dispatcher: Option[String] = None
                                   , actorClass: Class[_ >: PersistentServiceActor[T]] = classOf[PersistentServiceActor[T]]
                                   , actorArgs: Seq[Any] = Seq.empty[Any]
                                   , sharded: Boolean = false
                                   ) extends PersistentService[T] {

  require(snapshotInterval >= 0)

  override def withValue(t: Option[T]): PersistentService[T] = copy(value = t)

  override def withTimeout(d: Duration): PersistentService[T] = copy(timeout = d)

  override def withSnapshotInterval(i: Int): PersistentService[T] = {
    require(snapshotInterval >= 0)
    copy(snapshotInterval = i)
  }

  override def withDispatcher(d: String): PersistentService[T] = copy(dispatcher = Some(d))

  override def withActor(c: Class[_ >: PersistentServiceActor[T]]): PersistentService[T] = copy(actorClass = c)

  override def withArgs(args: Any*): PersistentService[T] = copy(actorArgs = args)

  override def withSharding(b: Boolean): PersistentService[T] = copy(sharded = b)

  override def command(cmd: CommandReceive[T]): PersistentService[T] = copy(commands = cmd +: this.commands)

  override def event(evt: EventReceive[T]): PersistentService[T] = copy(events = evt +: events)

  override def props: Props = {
    val p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p.withDispatcher(dispatcher.get)
    else p
  }

}
