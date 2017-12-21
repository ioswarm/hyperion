package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.server.Route
import akka.routing.RouterConfig
import de.ioswarm.hyperion.Service.{CommandReceive, EventReceive}

import scala.concurrent.duration._

object Service {

  type ServiceReceive = Context => Actor.Receive
  type ServiceRoute = ActorRef => Route

  type CommandReceive[T] = Option[T] => PartialFunction[Command, Action[Event]]
  type EventReceive[T] = Option[T] => PartialFunction[Event, T]

  object emptyBehavior extends ServiceReceive {
    override def apply(ctx: Context): Actor.Receive = Actor.emptyBehavior
  }

  object emptyRoute extends ServiceRoute {
    import akka.http.scaladsl.server.Directives._
    override def apply(ref: ActorRef): Route = reject
  }

  val defaultIdExtractor: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id, cmd)
  }
  val defaultShardResolver: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.id.hashCode) % 100).toString
  }

  def apply[A](
                name: String
                , actorClass: Class[_ <: ServiceActor[A]]
                , receive: ServiceReceive = emptyBehavior
                , route: ServiceRoute = emptyRoute
                , actorArgs: Seq[Any] = Seq.empty[Any]
                , dispatcher: Option[String] = None
                , router: Option[RouterConfig] = None
                , children: Seq[Service[_]] = Seq.empty[Service[_]]
              ): DefaultService[A] = new DefaultServiceImpl[A](
    name
    , receive
    , route
    , actorClass
    , actorArgs
    , dispatcher
    , router
    , children
  )

  def default(
           name: String
           , receive: ServiceReceive = emptyBehavior
           , route: ServiceRoute = emptyRoute
           , children: Seq[Service[_]] = Seq.empty[Service[_]]
           ): DefaultService[NotUsed] = apply(
    name = name
    , actorClass = classOf[DefaultServiceActor]
    , receive = receive
    , route = route
    , children = children
  )

  def service(
               name: String
               , receive: ServiceReceive = emptyBehavior
               , route: ServiceRoute = emptyRoute
               , children: Seq[Service[_]] = Seq.empty[Service[_]]
             ): Service[NotUsed] = default(
                name
                , receive
                , route
                , children
             )

}
sealed trait Service[A] {
  type ServiceReceive = Service.ServiceReceive
  type ServiceRoute = Service.ServiceRoute

  def name: String

  def actorClass: Class[_ <: ServiceActor[A]]
  def actorArgs: Seq[Any]

  def children: Seq[Service[_]]

  def withActor[B](clazz: Class[_ <: ServiceActor[B]]): Service[B]
  def withArgs(args: Any*): Service[A]

  def props: Props

  def :=(children: Seq[Service[_]]): Service[A]
  def +=(child: Service[_]): Service[A]
  def ++=(children: Seq[Service[_]]): Service[A]

  def withServices(children: Seq[Service[_]]): Service[A] = :=(children)
  def appendService(child: Service[_]): Service[A] = +=(child)
  def appendServices(children: Seq[Service[_]]): Service[A] = ++=(children)

}
trait DefaultService[A] extends Service[A] {

  def receive: ServiceReceive
  def route: ServiceRoute

  def dispatcher: Option[String]
  def router: Option[RouterConfig]

  def hasRoute: Boolean = route != Service.emptyRoute
  def hasReceive: Boolean = receive != Service.emptyBehavior

  def props: Props = {
    var p = Props(actorClass, this +: actorArgs :_*)
    if (dispatcher.isDefined) p = p.withDispatcher(dispatcher.get)
    if (router.isDefined) p = p.withRouter(router.get)
    p.copy()
  }

  def withReceive(receive: ServiceReceive): Service[A]
  def withRoute(route: ServiceRoute): Service[A]

  def withDispatcher(d: String): Service[A]
  def withRouter(r: RouterConfig): Service[A]

}

private[hyperion] case class DefaultServiceImpl[A](
                           name: String
                           , receive: Service.ServiceReceive
                           , route: Service.ServiceRoute
                           , actorClass: Class[_ <: ServiceActor[A]]
                           , actorArgs: Seq[Any]
                           , dispatcher: Option[String]
                           , router: Option[RouterConfig]
                           , children: Seq[Service[_]]
                         ) extends DefaultService[A] {

  override def withReceive(serviceReceive: ServiceReceive): Service[A] = copy(receive = serviceReceive)
  override def withRoute(serviceRoute: ServiceRoute): Service[A] = copy(route = serviceRoute)

  def withActor[B](clazz: Class[_ <: ServiceActor[B]]): Service[B] = DefaultServiceImpl(
    name
    , receive
    , route
    , clazz
    , actorArgs
    , dispatcher
    , router
    , children
  )
  def withArgs(args: Any*): Service[A] = copy(actorArgs = args)

  def withDispatcher(d: String): Service[A] = copy(dispatcher = Some(d))
  def withRouter(r: RouterConfig): Service[A] = copy(router = Some(r))

  def :=(cc: Seq[Service[_]]): Service[A] = copy(children = cc)
  def +=(c: Service[_]): Service[A] = copy(children = this.children :+ c)
  def ++=(cc: Seq[Service[_]]): Service[A] = copy(children = this.children ++ cc)

}

trait EventService[A] extends Service[A] {

  type CommandReceive[T] = Service.CommandReceive[T]
  type EventReceive[T] = Service.EventReceive[T]

  override def actorArgs: Seq[Any] = Seq.empty

  override def children: Seq[Service[_]] = Seq.empty

  def withActor[B](clazz: Class[_ <: ServiceActor[B]]): Service[B] = throw new UnsupportedOperationException
  def withArgs(args: Any*): Service[A] = throw new UnsupportedOperationException

  override def :=(children: Seq[Service[_]]): Service[A] = throw new UnsupportedOperationException
  override def +=(child: Service[_]): Service[A] = throw new UnsupportedOperationException
  override def ++=(children: Seq[Service[_]]): Service[A] = throw new UnsupportedOperationException

  def value: Option[A]
  def value(a: A): EventService[A]

  def shardName: String = name+"Entity"

  def idExtractor: ShardRegion.ExtractEntityId
  def idExtractor(extractor: ShardRegion.ExtractEntityId): EventService[A]

  def shardResolver: ShardRegion.ExtractShardId
  def shardResolver(resolver: ShardRegion.ExtractShardId): EventService[A]

  def receiveTimeout: Duration
  def receiveTimeout(d: Duration): EventService[A]

  def snapshotInterval: Int
  def snapshotInterval(i: Int): EventService[A]

  def commands: Seq[CommandReceive[A]]
  def events: Seq[EventReceive[A]]

  def props: Props = Props(actorClass, this)

  def command(cmd: CommandReceive[A]): EventService[A]
  def event(evt: EventReceive[A]): EventService[A]

  def commandReceive(value: Option[A]): PartialFunction[Command, Action[Event]] = {
    require(commands.nonEmpty)
    val first = commands.head
    commands.aggregate(first(value))( { (p, cmd) => p orElse cmd(value) }, { (p1, p2) => p1 orElse p2 })
  }

  def eventReceive(value: Option[A]): PartialFunction[Event, A] = {
    require(events.nonEmpty)
    val first = events.head
    events.aggregate(first(value))({(p, evt) => p orElse evt(value)}, {(p1, p2) => p1 orElse p2})
  }

  def persistentProps: Props = Props(new PersistentServiceActor(this))

  def asProxy: EventService[A]
  def asService: EventService[A]

}

private[hyperion] case class EventServiceImpl[A](
                                                name: String
                                                , value: Option[A] = None
                                                , commands: Seq[CommandReceive[A]] = Seq.empty
                                                , events: Seq[EventReceive[A]] = Seq.empty
                                                , idExtractor: ShardRegion.ExtractEntityId = Service.defaultIdExtractor
                                                , shardResolver: ShardRegion.ExtractShardId = Service.defaultShardResolver
                                                , receiveTimeout: Duration = 300.seconds
                                                , snapshotInterval: Int = Int.MaxValue
                                                , actorClass: Class[_ <: ServiceActor[A]] = classOf[EventServiceActor[A]]
                                             ) extends EventService[A] {

  require(snapshotInterval != 0)

  def value(a: A): EventService[A] = copy(value = Some(a))

  def idExtractor(extractor: ShardRegion.ExtractEntityId): EventService[A] = copy(idExtractor = extractor)

  def shardResolver(resolver: ShardRegion.ExtractShardId): EventService[A] = copy(shardResolver = resolver)

  def receiveTimeout(d: Duration): EventService[A] = copy(receiveTimeout = d)

  def snapshotInterval(i: Int): EventService[A] = copy(snapshotInterval = i)

  def command(cmd: CommandReceive[A]): EventService[A] = copy(commands = cmd +: this.commands)

  def event(evt: EventReceive[A]): EventService[A] = copy(events = evt +: events)

  def asProxy: EventService[A] = copy(actorClass = classOf[EventServiceProxy[A]])
  def asService: EventService[A] = copy(actorClass = classOf[EventServiceActor[A]])

}

