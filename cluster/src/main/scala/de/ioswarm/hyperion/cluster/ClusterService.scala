package de.ioswarm.hyperion.cluster

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import de.ioswarm.hyperion.{AkkaProvider, Hyperion, Service, ServiceFacade, ServiceOptions}

trait ClusterService extends Service


trait SingletonService extends ClusterService {
  def service: Service
  def terminationMessage: Any
  def role: Option[String]

  override def name: String = service.name

  override def props: Props = service.props

  override def route: ServiceRoute = service.route

  override def run(implicit provider: AkkaProvider): ActorRef = {
    val mgrRef = provider.actorOf(
      ClusterSingletonManager.props(
        singletonProps = props
        , terminationMessage = terminationMessage
        , settings = ClusterSingletonManagerSettings(provider.system)
          .withRole(role)
      )
      , name)

    val ref = provider.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = mgrRef.path.toStringWithoutAddress
        , settings = ClusterSingletonProxySettings(provider.system)
          .withRole(role)
      )
      , s"$name-proxy"
    )
    if (hasRoute) provider.hyperionRef ! Hyperion.HttpAppendRoute(route(ref))
    ref
  }

}

trait SingletonServiceFacade[A <: SingletonServiceFacade[A]] extends SingletonService with ServiceFacade[A] {

  def withTerminationMessage(message: Any): A
  def withRole(role: String): A

}

case class DefaultSingletonService(
                                    service: Service
                                    , terminationMessage: Any = PoisonPill
                                    , role: Option[String] = None
                                    , options: ServiceOptions = ServiceOptions()
                                  ) extends SingletonServiceFacade[DefaultSingletonService] {

  override def withTerminationMessage(message: Any): DefaultSingletonService = copy(terminationMessage = message)

  override def withRole(r: String): DefaultSingletonService = copy(role = Some(r))

  override def withName(n: String): DefaultSingletonService = throw new UnsupportedOperationException()

  override def withRoute(r: ServiceRoute): DefaultSingletonService = throw new UnsupportedOperationException()

  override def withOptions(opt: ServiceOptions): DefaultSingletonService = copy(options = opt)
}


trait ShardingService extends ClusterService {
  def service: Service
  def extractEntityId: ShardRegion.ExtractEntityId
  def extractShardId: ShardRegion.ExtractShardId
  def role: Option[String]

  override def name: String = service.name

  override def route: ServiceRoute = service.route

  override def props: Props = service.props


  override def run(implicit provider: AkkaProvider): ActorRef = {
    val ref = ClusterSharding(provider.system)
      .start(
        typeName = name
        , entityProps = props
        , settings = ClusterShardingSettings(provider.system)
        , extractEntityId = extractEntityId
        , extractShardId = extractShardId
      )
    if (hasRoute) provider.hyperionRef ! Hyperion.HttpAppendRoute(route(ref))
    ref
  }
}

trait ShardingServiceFacade[A <: ShardingServiceFacade[A]] extends ShardingService with ServiceFacade[A] {

  def withExtractEntityId(eei: ShardRegion.ExtractEntityId): A
  def withExtractShardId(esi: ShardRegion.ExtractShardId): A
  def withRole(role: String): A

}

case class DefaultShardingService(
                                 service: Service
                                 , extractEntityId: ShardRegion.ExtractEntityId = defaultExtractEntityId
                                 , extractShardId: ShardRegion.ExtractShardId = defaultExtractShardId
                                 , role: Option[String] = None
                                 , options: ServiceOptions = ServiceOptions()
                                 ) extends ShardingServiceFacade[DefaultShardingService] {

  def withExtractEntityId(eei: ShardRegion.ExtractEntityId): DefaultShardingService = copy(extractEntityId = eei)
  def withExtractShardId(esi: ShardRegion.ExtractShardId): DefaultShardingService = copy(extractShardId = esi)
  def withRole(role: String): DefaultShardingService = copy(role = Some(role))

  override def withName(n: String): DefaultShardingService = throw new UnsupportedOperationException()

  override def withRoute(r: ServiceRoute): DefaultShardingService = throw new UnsupportedOperationException()

  override def withOptions(opt: ServiceOptions): DefaultShardingService = copy(options = opt)
}