package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.Service.{ServiceReceive, ServiceRoute}

import scala.collection.immutable


/**
  * Created by andreas on 31.10.17.
  */
object Service {
  type ServiceReceive = Context => Actor.Receive
  type ServiceRoute = ActorRef => Route

  object emptyBehavior extends ServiceReceive {
    override def apply(ctx: Context): Receive = Actor.emptyBehavior
  }

  object emptyRoute extends ServiceRoute {
    import akka.http.scaladsl.server.Directives._
    override def apply(ref: ActorRef): Route = reject
  }

}
trait Service[A] {
  type ServiceReceive = Service.ServiceReceive
  type ServiceRoute = Service.ServiceRoute

  def name: String
  def receive: ServiceReceive
  def route: ServiceRoute
  def actorClass: Class[_ <: ServiceActor[A]]
  def actorArgs: immutable.Seq[Any]
  def children: immutable.Seq[Service[_]]

  def hasRoute: Boolean = route != Service.emptyRoute

  def props: Props = Props(actorClass, this +: actorArgs :_*)

  def withRoute(route: ServiceRoute): Service[A]

}

case class ServiceImpl[A](
                         name: String
                         , receive: ServiceReceive
                         , route: ServiceRoute
                         , actorClass: Class[_ <: ServiceActor[A]]
                         , actorArgs: immutable.Seq[Any]
                         , children: immutable.Seq[Service[_]]
                         ) extends Service[A] {

  override def withRoute(serviceRoute: ServiceRoute): Service[A] = copy(route = serviceRoute)

}

sealed case class DefaultService(name: String
                          , receive: ServiceReceive
                          , route: ServiceRoute
                          , children: immutable.Seq[Service[_]]
                         ) extends Service[NotUsed] {

  def actorClass: Class[_ <: ServiceActor[NotUsed]] = classOf[DefaultServiceActor]
  def actorArgs: immutable.Seq[Any] = immutable.Seq.empty[Any]

  override def withRoute(serviceRoute: ServiceRoute): Service[NotUsed] = copy(route = serviceRoute)

}
