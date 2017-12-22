package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{Actor, ActorRef}


/**
  * Created by andreas on 05.11.17.
  */
trait DefaultServiceApply {

  private def createDefaultService(name: String, children: Service[_]*)(f: Service.ServiceReceive): Service[NotUsed] = Service.default(name, f, Service.emptyRoute, children.toList)

  def create(name: String)(f: Service.ServiceReceive): Service[NotUsed] = createDefaultService(name)(f)

  def create[S1](name: String, s1: Service[S1])(f: Context => (ActorRef) => Actor.Receive): Service[NotUsed] = createDefaultService(name, s1){ ctx =>
    val ref1 = ctx.actorOf(s1)
    f(ctx)(ref1)
  }

  def create[S1, S2](name: String, s1: Service[S1], s2: Service[S2])(f: Context => (ActorRef, ActorRef) => Actor.Receive): Service[NotUsed] = createDefaultService(name, s1, s2){ ctx =>
    val ref1 = ctx.actorOf(s1)
    val ref2 = ctx.actorOf(s2)
    f(ctx)(ref1, ref2)
  }

}
