package de.ioswarm.hyperion

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.Hyperion.HttpAppendRoute

trait CoreImplicits {

  implicit class HYStringExtender(s: String) {

    def withProps(props: Props): DefaultService = DefaultService(s, props)

    def withActor(clazz: Class[_]): DefaultService = withProps(Props(clazz))

    def receive(receive: ServiceReceive): DefaultReceivableService = Service(s, receive)

  }

  implicit class HYServiceExtender(s: Service) {

    def run(implicit provider: AkkaProvider): ActorRef = provider.actorOf(s)

  }

  implicit class HYDefaultServiceExtender(ds: DefaultService) {

    def withReceive(r: ServiceReceive): DefaultReceivableService = DefaultReceivableService(
      name = ds.name
      , receive = r
      , route = ds.route
      , dispatcher = ds.dispatcher
      , mailbox = ds.mailbox
      , router = ds.router
    )

  }

  def run(service: Service)(implicit provider: AkkaProvider): ActorRef = provider.actorOf(service)
  def applyRoute(route: Route)(implicit provider: AkkaProvider): Unit = provider.hyperionRef ! HttpAppendRoute(route)

}
