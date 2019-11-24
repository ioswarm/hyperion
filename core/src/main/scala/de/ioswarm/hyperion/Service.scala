package de.ioswarm.hyperion

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.model.{Action, Command, Event}

import scala.concurrent.Future

object Service {

  type ServiceReceive = ServiceContext => Actor.Receive
  type ServiceRoute = ActorRef => Route

  type ServiceCall = ServiceContext => Unit

  type CommandReceive[T] = ServiceContext => T => PartialFunction[Command, Action]
  type EventReceive[T] = ServiceContext => T => PartialFunction[Event, T]

  type OnCreate[L, E] = L => E => Future[Option[E]]
  type OnRead[R, E] = R => Future[Option[E]]
  type OnUpdate[R, E] = R => E => Future[Option[E]]
  type OnDelete[R, E] = R => Future[Option[E]]

  object emptyBehavior extends ServiceReceive {
    override def apply(ctx: ServiceContext): Actor.Receive = Actor.emptyBehavior
  }

  object emptyRoute extends ServiceRoute {
    import akka.http.scaladsl.server.Directives._
    override def apply(ref: ActorRef): Route = reject
  }

  object emptyCall extends ServiceCall {
    override def apply(ctx: ServiceContext): Unit = ()
  }

  object defaultOnTimeout extends ServiceCall {
    override def apply(v1: ServiceContext): Unit = v1.self ! Hyperion.Stop
  }

  def apply(name: String, props: Props): DefaultService = DefaultService(name, props)
  def apply(props: Props): DefaultService = apply(UUID.randomUUID().toString, props)

  def apply(name: String, route: ServiceRoute): DefaultService = DefaultService(name, Props.empty, route)
  def apply(route: ServiceRoute): DefaultService = apply(UUID.randomUUID().toString, route)

  def apply(name: String, receive: ServiceReceive): DefaultReceivableService = DefaultReceivableService(name, receive)
  def apply(receive: ServiceReceive): DefaultReceivableService = apply(UUID.randomUUID().toString, receive)

}

trait Service { self =>

  type ServiceRoute = Service.ServiceRoute

  def name: String
  def route: ServiceRoute
  def options: ServiceOptions

  def props: Props

  def hasRoute: Boolean = route != Service.emptyRoute

  def run(implicit provider: AkkaProvider): ActorRef = {
    val ref = provider.actorOf(props, name)
    provider.log.debug("Run service '{}' at {}", name, ref.path.toString)
    if (hasRoute) provider.hyperionRef ! Hyperion.HttpAppendRoute(route(ref))
    ref
  }

}

trait ServiceFacade[A <: ServiceFacade[A]] extends Service {

  def withName(n: String): A
  def withRoute(r: ServiceRoute): A
  def route(r: ServiceRoute): A = withRoute(r)
  def withServiceOptions(opt: ServiceOptions): A = withOptions(opt)
  def withOptions(opt: ServiceOptions): A

}

final case class DefaultService(
                         name: String
                         , props: Props
                         , route: Service.ServiceRoute = Service.emptyRoute
                         , options: ServiceOptions = ServiceOptions()
                         ) extends ServiceFacade[DefaultService] {

  override def withName(n: String): DefaultService = copy(name = n)
  override def withRoute(r: ServiceRoute): DefaultService = copy(route = r)
  override def withOptions(opt: ServiceOptions): DefaultService = copy(options = opt)
}

trait ReceivableService extends Service {

  def receive: Service.ServiceReceive

}

trait ReceivableServiceFacade[A <: ReceivableServiceFacade[A]] extends ReceivableService with ServiceFacade[A] {

  def withReceive(r: Service.ServiceReceive): A

}

final case class DefaultReceivableService(
                                   name: String
                                   , receive: Service.ServiceReceive = Service.emptyBehavior
                                   , route: Service.ServiceRoute = Service.emptyRoute
                                   , options: ServiceOptions = ServiceOptions(actorClass = classOf[ReceivableServiceActor])
                                   ) extends ReceivableServiceFacade[DefaultReceivableService] {

  override def withReceive(r: Service.ServiceReceive): DefaultReceivableService = copy(receive = r)

  override def withRoute(r: ServiceRoute): DefaultReceivableService = copy(route = r)

  override def withName(n: String): DefaultReceivableService = copy(name = n)

  override def withOptions(opt: ServiceOptions): DefaultReceivableService = copy(options = opt)

  override def props: Props = Props(options.actorClass, this)
    .withDispatcher(options.dispatcher)
    .withMailbox(options.mailbox)
    .withRouter(options.routerConfig)

}


trait PersistentService[T] extends Service {

  def value: T
  def commandReceive: Service.CommandReceive[T]
  def eventReceive: Service.EventReceive[T]
  def snapshotInterval: Int

}

trait PersistentServiceFacade[T, A <: PersistentServiceFacade[T, A]] extends PersistentService[T] with ServiceFacade[A] {

  def withCommandReceive(cr: Service.CommandReceive[T]): A
  def command(cr: Service.CommandReceive[T]): A = withCommandReceive(cr)
  def withEventReceive(er: Service.EventReceive[T]): A
  def event(er: Service.EventReceive[T]): A = withEventReceive(er)
  def withSnapshotInterval(si: Int): A

}

final case class DefaultPersistentService[T](
                                     name: String
                                     , value: T
                                     , commandReceive: Service.CommandReceive[T]
                                     , eventReceive: Service.EventReceive[T]
                                     , snapshotInterval: Int = Int.MaxValue
                                     , route: Service.ServiceRoute = Service.emptyRoute
                                     , options: ServiceOptions = ServiceOptions(actorClass = classOf[PersistentServiceActor[T]])
                                   ) extends PersistentServiceFacade[T, DefaultPersistentService[T]] {

  override def withCommandReceive(cr: Service.CommandReceive[T]): DefaultPersistentService[T] = copy(commandReceive = cr)

  override def withEventReceive(er: Service.EventReceive[T]): DefaultPersistentService[T] = copy(eventReceive = er)

  override def withSnapshotInterval(si: Int): DefaultPersistentService[T] = copy(snapshotInterval = si)

  override def withName(n: String): DefaultPersistentService[T] = copy(name = n)

  override def withOptions(opt: ServiceOptions): DefaultPersistentService[T] = copy(options = opt)

  override def withRoute(r: ServiceRoute): DefaultPersistentService[T] = copy(route = r)

  override def props: Props = Props(options.actorClass, this)
    .withDispatcher(options.dispatcher)
    .withMailbox(options.mailbox)
    .withRouter(options.routerConfig)

}

trait AppendableService extends Service {
  def children: List[Service]
}

trait AppendableServiceFacade[A <: AppendableServiceFacade[A]] extends AppendableService with ServiceFacade[A] {

  def appendService(child: Service): A
  def +(child: Service): A = appendService(child)

}
