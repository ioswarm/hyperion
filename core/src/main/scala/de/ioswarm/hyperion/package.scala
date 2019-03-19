package de.ioswarm

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.server.Route

package object hyperion {

  type ServiceReceive = ServiceContext => Actor.Receive
  type ServiceRoute = ActorRef => Route

  type ServiceCall = ServiceContext => Unit


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

}
