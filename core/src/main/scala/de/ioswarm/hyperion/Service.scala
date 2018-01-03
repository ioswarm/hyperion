package de.ioswarm.hyperion

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.server.Route
import akka.routing.RouterConfig


object Service {
  type ServiceReceive = ServiceContext => Actor.Receive
  type ServiceRoute = ActorRef => Route

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
