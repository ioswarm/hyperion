package de.ioswarm.hyperion

import akka.actor.{ActorRef, Props}

import scala.concurrent.Future

object Implicits {

  implicit class ServiceExtender(s: Service) {

    def run(implicit hy: Hyperion): Future[ActorRef] = hy.run(s)

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

  }

}
