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
                               , onTimeout: ServiceCall = emptyCall
                               , preStart: ServiceCall = emptyCall
                               , postStop: ServiceCall = emptyCall
                               ) {

  def withServiceActor(clazz: Class[_ <: ServiceActor]): ServiceOptions = copy(actorClass = clazz)

  def withSupervisorStrategy(strategy: SupervisorStrategy): ServiceOptions = copy(supervisorStrategy = strategy)
  def withDispatcher(dispatcherName: String): ServiceOptions = copy(dispatcher = dispatcherName)
  def withMailbox(mailboxName: String): ServiceOptions = copy(mailbox = mailboxName)
  def withRouterConfig(config: RouterConfig): ServiceOptions = copy(routerConfig = config)
  def withTimeout(duration: Duration): ServiceOptions = copy(timeout = duration)

  def withOnTimeout(call: ServiceCall): ServiceOptions = copy(onTimeout = call)
  def withPreStart(call: ServiceCall): ServiceOptions = copy(preStart = call)
  def withPostStop(call: ServiceCall): ServiceOptions = copy(postStop = call)

}
