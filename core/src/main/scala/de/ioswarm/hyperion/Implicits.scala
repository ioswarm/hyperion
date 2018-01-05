package de.ioswarm.hyperion

import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}

import scala.concurrent.Future

object Implicits {

  implicit class ServiceExtender(s: Service) {

    def run(implicit hy: Hyperion): Future[ActorRef] = hy.run(s)

    def route(route: Service.ServiceRoute): HttpService = HttpServiceImpl(s, route)

    def asSingleton(implicit hy: Hyperion): Service = SingletonServiceImpl(s)

  }

  implicit class ActorServiceExtender(a: ActorService) {

    import Service._

    /*def route(r: ServiceRoute): HttpService = HttpServiceImpl(
      a.name
      , a.receive
      , r
      , a.dispatcher
      , a.router
      , a.actorClass
      , a.actorArgs
      , a.children
    )*/

    def sharding(entityIdExtract: ExtractEntityId = Service.defaultExtractEntityId
                 , shardIdExtract: ExtractShardId = Service.defaultExtractShardId): ShardingServiceImpl[ActorService] = ShardingServiceImpl(
      a
      , entityIdExtract
      , shardIdExtract
    )

  }

  implicit class PersistentServiceExtender[T](p: PersistentService[T]) {

    def sharding(entityIdExtract: ExtractEntityId = Service.defaultExtractEntityId
                 , shardIdExtract: ExtractShardId = Service.defaultExtractShardId): ShardingServiceImpl[PersistentService[T]] = ShardingServiceImpl(
      p.withSharding(true)
      , entityIdExtract
      , shardIdExtract
    )

  }

  implicit class StringExtender(s: String) {

    import Service._

    def props(p: Props): Service = forward(p)

    def ref(ref: ActorRef): Service = forward(ref)

    def receive(r: ServiceReceive): ActorService = ActorServiceImpl(name = s, receive = r)

//    def route(r: ServiceRoute): HttpService = HttpServiceImpl(name = s, route = r)

    def forward(p: Props): Service = PropsForwardServiceImpl(s, p)

    def forward(ref: ActorRef): Service = ActorRefForwardServiceImpl(s, ref)

    def persist[T]: PersistentService[T] = persist[T](None)
    def persist[T](t: Option[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, value = t)
//    def command[T](cmd: CommandReceive[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, commands=List(cmd))
//    def event[T](evt: EventReceive[T]): PersistentService[T] = PersistentServiceImpl[T](name = s, events = List(evt))

  }

}
