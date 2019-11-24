package de.ioswarm.hyperion

import akka.actor.{Deploy, SupervisorStrategy}
import akka.routing.{NoRouter, RouterConfig}

import scala.concurrent.duration.Duration

final case class ServiceOptions(
                               actorClass: Class[_] = classOf[ServiceActor]
                               , supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy
                               , dispatcher: String = Deploy.NoDispatcherGiven
                               , mailbox: String = Deploy.NoMailboxGiven
                               , routerConfig: RouterConfig = NoRouter
                               , timeout: Duration = Duration.Inf
                               , onTimeout: Service.ServiceCall = Service.emptyCall
                               , preStart: Service.ServiceCall = Service.emptyCall
                               , postStop: Service.ServiceCall = Service.emptyCall
                               ) {

  def withServiceActor(clazz: Class[_ <: ServiceActor]): ServiceOptions = copy(actorClass = clazz)

  def withSupervisorStrategy(strategy: SupervisorStrategy): ServiceOptions = copy(supervisorStrategy = strategy)
  def withDispatcher(dispatcherName: String): ServiceOptions = copy(dispatcher = dispatcherName)
  def withMailbox(mailboxName: String): ServiceOptions = copy(mailbox = mailboxName)
  def withRouterConfig(config: RouterConfig): ServiceOptions = copy(routerConfig = config)
  def withTimeout(duration: Duration): ServiceOptions = copy(timeout = duration)

  def withOnTimeout(call: Service.ServiceCall): ServiceOptions = copy(onTimeout = call)
  def withPreStart(call: Service.ServiceCall): ServiceOptions = copy(preStart = call)
  def withPostStop(call: Service.ServiceCall): ServiceOptions = copy(postStop = call)

}
