package de.ioswarm.hyperion

import java.util.UUID

import akka.actor.{Actor, Deploy, Props, SupervisorStrategy}
import akka.routing.{NoRouter, RouterConfig}


object Service {

  def apply(name: String, props: Props): DefaultService = DefaultService(name, props)
  def apply(props: Props): DefaultService = apply(UUID.randomUUID().toString, props)

  def apply(name: String, route: ServiceRoute): DefaultService = DefaultService(name, Props.empty, route)
  def apply(route: ServiceRoute): DefaultService = apply(UUID.randomUUID().toString, route)

  def apply(name: String, receive: ServiceReceive): DefaultReceivableService = DefaultReceivableService(name, receive)
  def apply(receive: ServiceReceive): DefaultReceivableService = apply(UUID.randomUUID().toString, receive)

}

trait Service {

  def name: String
  def props: Props
  def route: ServiceRoute

  def hasRoute: Boolean = route != emptyRoute

}

trait ServiceFacade[A <: ServiceFacade[A]] extends Service {

  def dispatcher: String // = Deploy.NoDispatcherGiven
  def mailbox: String // = Deploy.NoMailboxGiven
  def router: RouterConfig // = NoRouter

  def withName(n: String): A
  def withDispatcher(d: String): A
  def withMailbox(m: String): A
  def withRouter(r: RouterConfig): A

  def withRoute(r: ServiceRoute): A
  def route(r: ServiceRoute): A = withRoute(r)

}

object DefaultService {

  def apply(
            name: String
            , props: Props
            , route: ServiceRoute = emptyRoute
            , dispatcher: String = Deploy.NoDispatcherGiven
            , mailbox: String = Deploy.NoMailboxGiven
            , router: RouterConfig = NoRouter
           ): DefaultService = DefaultService(
    name
    , props
      .withDispatcher(dispatcher)
      .withMailbox(mailbox)
      .withRouter(router)
    , route
  )

}
case class DefaultService(
                         name: String
                         , props: Props
                         , route: ServiceRoute
                         ) extends ServiceFacade[DefaultService] {

  override def withName(n: String): DefaultService = copy(name = n)

  override def withRoute(r: ServiceRoute): DefaultService = copy(route = r)

  override def withDispatcher(d: String): DefaultService = copy(props = this.props.withDispatcher(d))

  override def withMailbox(m: String): DefaultService = copy(props = this.props.withMailbox(m))

  override def withRouter(r: RouterConfig): DefaultService = copy(props = this.props.withRouter(r))

  override def dispatcher: String = props.dispatcher

  override def mailbox: String = props.mailbox

  override def router: RouterConfig = props.routerConfig
}

trait ReceivableService extends Service {

  def actorClass: Class[_]
  def receive: ServiceReceive
  def supervisorStrategy: SupervisorStrategy // = SupervisorStrategy.defaultStrategy
  @throws(classOf[Exception])
  def preStart: ServiceCall // = emptyCall
  @throws(classOf[Exception])
  def postStop: ServiceCall // = emptyCall

}

trait ReceivableServiceFacade[A <: ReceivableServiceFacade[A]] extends ReceivableService with ServiceFacade[A] {

  def withActorClass(clazz: Class[_]): A
  def withReceive(r: ServiceReceive): A
  def withSupervisorStrategy(s: SupervisorStrategy): A
  def withPreStart(ps: ServiceCall): A
  def withPostStop(ps: ServiceCall): A

}

case class DefaultReceivableService(
                                   name: String
                                   , receive: ServiceReceive = emptyBehavior
                                   , route: ServiceRoute = emptyRoute
                                   , preStart: ServiceCall = emptyCall
                                   , postStop: ServiceCall = emptyCall
                                   , supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy
                                   , dispatcher: String = Deploy.NoDispatcherGiven
                                   , mailbox: String = Deploy.NoMailboxGiven
                                   , router: RouterConfig = NoRouter
                                   , actorClass: Class[_] = classOf[ReceivableServiceActor]
                                   ) extends ReceivableServiceFacade[DefaultReceivableService] {

  override def withReceive(r: ServiceReceive): DefaultReceivableService = copy(receive = r)

  override def withRoute(r: ServiceRoute): DefaultReceivableService = copy(route = r)

  override def withSupervisorStrategy(s: SupervisorStrategy): DefaultReceivableService = copy(supervisorStrategy = s)

  override def withPreStart(ps: ServiceCall): DefaultReceivableService = copy(preStart = ps)

  override def withPostStop(ps: ServiceCall): DefaultReceivableService = copy(postStop = ps)

  override def withName(n: String): DefaultReceivableService = copy(name = n)

  override def withDispatcher(d: String): DefaultReceivableService = copy(dispatcher = d)

  override def withMailbox(m: String): DefaultReceivableService = copy(mailbox = m)

  override def withRouter(r: RouterConfig): DefaultReceivableService = copy(router = r)

  override def withActorClass(clazz: Class[_]): DefaultReceivableService = copy(actorClass = clazz)

  override def props: Props = Props(actorClass, this)
    .withDispatcher(dispatcher)
    .withMailbox(mailbox)
    .withRouter(router)

}
