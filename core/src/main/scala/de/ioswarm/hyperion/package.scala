package de.ioswarm

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.model.{Action, Command, Event}

import scala.concurrent.Future

package object hyperion {

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

}
