package de.ioswarm.hyperion

import akka.actor.{ActorRef, Props}

import scala.concurrent.Future

object Implicits {

  implicit class ServiceExtender(s: Service) {

    def run(implicit hy: Hyperion): Future[ActorRef] = hy.run(s)

    def asSingleton(implicit hy: Hyperion): Service = SingletonServiceImpl(s)

  }

  implicit class ActorServiceExtender(a: ActorService) {

    import Service._

    def route(r: ServiceRoute): HttpService = HttpServiceImpl(
      a.name
      , a.receive
      , r
      , a.dispatcher
      , a.router
      , a.actorClass
      , a.actorArgs
      , a.children
    )

  }

  implicit class StringExtender(s: String) {

    import Service._

    def props(p: Props): Service = ForwardServiceImpl(s, p)

    def receive(r: ServiceReceive): ActorService = ActorServiceImpl(name = s, receive = r)

    def route(r: ServiceRoute): HttpService = HttpServiceImpl(name = s, route = r)

    def forward(p: Props): Service = ForwardServiceImpl(s, p)

    def persist[T]: PersistentService[T] = persist[T](None)
    def persist[T](t: Option[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, value = t)
//    def command[T](cmd: CommandReceive[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, commands=List(cmd))
    def event[T](evt: EventReceive[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, events = List(evt))

  }

}
