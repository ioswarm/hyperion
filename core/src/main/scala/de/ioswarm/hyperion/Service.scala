package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorContext, ActorPath, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.server.Route
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.query.scaladsl.ReadJournal
import akka.routing.RouterConfig
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.Config
import de.ioswarm.hyperion.Hyperion.Stop
import de.ioswarm.hyperion.Service.{CommandReceive, EventReceive, Pipe, ServiceReceive}
import de.ioswarm.hyperion.model.{Action, Command, Event}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object Service {
  type ServiceReceive = ServiceContext => Actor.Receive
  type ServiceRoute = ActorRef => Route

  type Pipe[Mat] = ServiceContext => Future[Mat]
  type Stream[Mat] = Pipe[Mat]

  type CommandReceive[T] = ServiceContext => Option[T] => PartialFunction[Command, List[Action[Event]]]
  type EventReceive[T] = ServiceContext => Option[T] => PartialFunction[Event, Option[T]]

  val defaultExtractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id, cmd)
  }
  val defaultExtractShardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.id.hashCode) % 100).toString
  }

  object emptyBehavior extends ServiceReceive {
    override def apply(ctx: ServiceContext): Actor.Receive = Actor.emptyBehavior
  }

  object emptyPipeBehavior extends ServiceReceive {
    override def apply(ctx: ServiceContext): Actor.Receive = {
      case _ => ctx.self ! Stop
    }
  }

  object emptyRoute extends ServiceRoute {
    import akka.http.scaladsl.server.Directives._
    override def apply(ref: ActorRef): Route = reject
  }

}
trait Service {

  def name: String
  def props: Props

  def config: Config = Hyperion.config

  def initialize: Boolean = true

  def createActor(implicit ac: ActorContext): ActorRef = ac.actorOf(props, name)

}

case class PropsForwardServiceImpl(name: String, actorProps: Props) extends Service {

  def props: Props = Props(classOf[PropsForwardServiceActor], actorProps)

}

case class ActorRefForwardServiceImpl(name: String, ref: ActorRef) extends Service {

  def props: Props = Props(classOf[ActorRefForwardServiceActor], ref)

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


trait HttpService extends Service {
  type ServiceRoute = Service.ServiceRoute

  def service: Service
  def route: ServiceRoute

  def hasRoute: Boolean = route != Service.emptyRoute

  def withRoute[T >: HttpService](route: ServiceRoute): T
  def route[T >: HttpService](route: ServiceRoute): T = withRoute(route)
}

case class HttpServiceImpl(service: Service, route: Service.ServiceRoute = Service.emptyRoute) extends HttpService {

  override def name: String = service.name

  override def props: Props = service.props

  override def createActor(implicit ac: ActorContext): ActorRef = service.createActor

  def withRoute[T >: HttpService](r: ServiceRoute): T = copy(route = r)
}

trait ManagerService extends Service {
  type ServiceRoute = Service.ServiceRoute

  def route: ServiceRoute

}

case class SingletonServiceImpl[T <:Service](service: T) extends Service {
  import Hyperion._

  override def name: String = service.name

  override def props: Props = service.props

  override def createActor(implicit ac: ActorContext): ActorRef = ac.actorOf(Props(classOf[PropsForwardServiceActor]
    , ClusterSingletonManager.props(
      singletonProps = props
      , terminationMessage = Stop
      , settings = ClusterSingletonManagerSettings(ac.system)
    ))
    , name)

}

case class SingletonProxyServiceImpl(name: String, path: String) extends Service {

  override def props: Props = throw new IllegalAccessException("No Props needed for SingletonProxies.")

  def proxyPath: String = {
    val ap = ActorPath.fromString(path)
    (ap / ap.name).toStringWithoutAddress
  }

  override def createActor(implicit ac: ActorContext): ActorRef = ac.actorOf(
    ClusterSingletonProxy.props(
      proxyPath
      , ClusterSingletonProxySettings(ac.system)
    )
    , name
  )

}

// TODO implement cluster-sharding-proxy-service
case class ShardingServiceImpl[T <: Service](
                                              service: T
                                              , entityIdExtract: ExtractEntityId = Service.defaultExtractEntityId
                                              , shardIdExtract: ExtractShardId = Service.defaultExtractShardId
                                            ) extends Service {

  override def name: String = service.name

  override def props: Props = service.props

//  override def initialize: Boolean = false

  override def createActor(implicit ac: ActorContext): ActorRef = ac.actorOf(Props(classOf[ActorRefForwardServiceActor]
    , ClusterSharding(ac.system).start(
      typeName = name
      , entityProps = props
      , settings = ClusterShardingSettings(ac.system)
      , extractEntityId = entityIdExtract
      , extractShardId = shardIdExtract
    ))
    , name)

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
  def queryStream: Option[QueryStream]

  def withValue(t: Option[T]): PersistentService[T]
  def withTimeout(d: Duration): PersistentService[T]
  def withSnapshotInterval(i: Int): PersistentService[T]
  def withDispatcher(d: String): PersistentService[T]
  def withActor(c: Class[_ >: PersistentServiceActor[T]]): PersistentService[T]
  def withArgs(args: Any*): PersistentService[T]
  def withSharding(b: Boolean): PersistentService[T]
  def withQueryStream(query: QueryStream): PersistentService[T]

  def command(c: CommandReceive[T]): PersistentService[T]
  def event(e: EventReceive[T]): PersistentService[T]

  def commandReceive(ctx: ServiceContext)(value: Option[T]): PartialFunction[Command, List[Action[Event]]] = {
    require(commands.nonEmpty)
    val first = commands.head
    commands.aggregate(first(ctx)(value))( { (p, cmd) => p orElse cmd(ctx)(value) }, { (p1, p2) => p1 orElse p2 })
  }

  def eventReceive(ctx: ServiceContext)(value: Option[T]): PartialFunction[Event, Option[T]] = {
    require(events.nonEmpty)
    val first = events.head
    events.aggregate(first(ctx)(value))({(p, evt) => p orElse evt(ctx)(value)}, {(p1, p2) => p1 orElse p2})
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
                                   , queryStream: Option[QueryStream] = None
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

  override def withQueryStream(query: QueryStream): PersistentService[T] = copy(queryStream = Some(query))

  override def command(cmd: CommandReceive[T]): PersistentService[T] = copy(commands = cmd +: this.commands)

  override def event(evt: EventReceive[T]): PersistentService[T] = copy(events = evt +: events)

  override def props: Props = {
    val p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p.withDispatcher(dispatcher.get)
    else p
  }

}

trait PersistenceQueryService[T <: ReadJournal] extends Service {

  def pluginId: String

  def journalReader(system: ActorSystem): T = PersistenceQuery(system).readJournalFor[T](pluginId)

  def source(system: ActorSystem, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed]

  def sink: Sink[EventEnvelope, NotUsed]

  override def props: Props = Props(classOf[PersistenceQueryServiceActor[T]], this)

}

trait PipeService[Mat] extends Service {

  def pipe: Service.Pipe[Mat]

  def receive: Service.ServiceReceive
  def dispatcher: Option[String]
  def router: Option[RouterConfig]

  def actorClass: Class[_ <: PipeServiceActor[Mat]]
  def actorArgs: Seq[Any]

  def hasReceive: Boolean = receive != Service.emptyPipeBehavior

  def withPipe[T >: PipeService[Mat]](pipe: Service.Pipe[Mat]): T

  def withReceive[T >: PipeService[Mat]](receive: Service.ServiceReceive): T
  def withDispatcher[T >: PipeService[Mat]](d: String): T
  def withRouter[T >: PipeService[Mat]](r: RouterConfig): T

  def withActor[T >: PipeService[Mat]](clazz: Class[_ <: PipeServiceActor[Mat]]): T
  def withArgs[T >: PipeService[Mat]](args: Any*): T

}
case class PipeServiceImpl[Mat](
                                name: String
                                , pipe: Service.Pipe[Mat]
                                , receive: Service.ServiceReceive = Service.emptyPipeBehavior
                                , dispatcher: Option[String] = None
                                , router: Option[RouterConfig] = None
                                , actorClass: Class[_ <: PipeServiceActor[Mat]] = classOf[PipeServiceActor[Mat]]
                                , actorArgs: Seq[Any] = Seq.empty[Any]
                                ) extends PipeService[Mat] {

  override def props: Props = {
    var p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p = p.withDispatcher(dispatcher.get)
    if (router.isDefined) p = p.withRouter(router.get)
    p.copy()
  }

  override def withPipe[T >: PipeService[Mat]](p: Service.Pipe[Mat]): T = copy(pipe = p)

  override def withReceive[T >: PipeService[Mat]](r: ServiceReceive): T = copy(receive = r)

  override def withDispatcher[T >: PipeService[Mat]](d: String): T = copy(dispatcher = Some(d))

  override def withRouter[T >: PipeService[Mat]](r: RouterConfig): T = copy(router = Some(r))

  override def withActor[T >: PipeService[Mat]](clazz: Class[_ <: PipeServiceActor[Mat]]): T = copy(actorClass = clazz)

  override def withArgs[T >: PipeService[Mat]](args: Any*): T = copy(actorArgs = args)

}

trait StreamingService[Mat] extends PipeService[Mat] {

  def stream: Service.Stream[Mat]

  def withStream[T >: StreamingService[Mat]](p: Service.Stream[Mat]): T

  override def pipe: Pipe[Mat] = stream
  override def withPipe[T >: StreamingService[Mat]](p: Service.Pipe[Mat]): T = withStream(p)

}
case class StreamingServiceImpl[Mat](
                                 name: String
                                 , stream: Service.Stream[Mat]
                                 , receive: Service.ServiceReceive = Service.emptyPipeBehavior
                                 , dispatcher: Option[String] = None
                                 , router: Option[RouterConfig] = None
                                 , actorClass: Class[_ <: PipeServiceActor[Mat]] = classOf[StreamServiceActor[Mat]]
                                 , actorArgs: Seq[Any] = Seq.empty[Any]
                               ) extends StreamingService[Mat] {

  override def props: Props = {
    var p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p = p.withDispatcher(dispatcher.get)
    if (router.isDefined) p = p.withRouter(router.get)
    p.copy()
  }

  override def withStream[T >: StreamingService[Mat]](p: Service.Stream[Mat]): T = copy(stream = p)

  override def withReceive[T >: StreamingService[Mat]](r: ServiceReceive): T = copy(receive = r)

  override def withDispatcher[T >: StreamingService[Mat]](d: String): T = copy(dispatcher = Some(d))

  override def withRouter[T >: StreamingService[Mat]](r: RouterConfig): T = copy(router = Some(r))

  override def withActor[T >: StreamingService[Mat]](clazz: Class[_ <: PipeServiceActor[Mat]]): T = copy(actorClass = clazz)

  override def withArgs[T >: StreamingService[Mat]](args: Any*): T = copy(actorArgs = args)

}
