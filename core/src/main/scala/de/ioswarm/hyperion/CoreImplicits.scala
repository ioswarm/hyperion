package de.ioswarm.hyperion

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.server.Route
import de.ioswarm.hyperion.Hyperion.HttpAppendRoute

trait CoreImplicits {

  implicit class HYStringExtender(s: String) {

    def withProps(props: Props): DefaultService = DefaultService(s, props)

    def withActor(clazz: Class[_]): DefaultService = withProps(Props(clazz))

    def receive(receive: Service.ServiceReceive): DefaultReceivableService = Service(s, receive)

    def command[T](data: T)(c: Service.CommandReceive[T]) = DefaultPersistentService(
      s
      , data
      , c
      , { implicit ctx => a: T => {
        case _ => a
      }}
    )

  }

  implicit class HYDefaultServiceExtender(ds: DefaultService) {

    def withReceive(r: Service.ServiceReceive): DefaultReceivableService = DefaultReceivableService(
      name = ds.name
      , receive = r
      , route = ds.route
      , options = ds.options.withServiceActor(classOf[ReceivableServiceActor])
    )

  }

  def run(service: Service)(implicit provider: AkkaProvider): ActorRef = service.run  //provider.actorOf(service)
  def applyRoute(route: Route)(implicit provider: AkkaProvider): Unit = provider.hyperionRef ! HttpAppendRoute(route)

}
