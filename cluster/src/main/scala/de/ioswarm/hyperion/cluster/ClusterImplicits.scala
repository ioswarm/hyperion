package de.ioswarm.hyperion.cluster

import akka.actor.PoisonPill
import akka.cluster.sharding.ShardRegion
import de.ioswarm.hyperion.Service

trait ClusterImplicits {

  implicit class HYClusterServiceExtender(s: Service) {

    def singleton(
                   terminationMessage: Any = PoisonPill
                   , role: Option[String] = None
                 ): DefaultSingletonService = DefaultSingletonService(s, terminationMessage, role)

    def sharded(
                 extractEntityId: ShardRegion.ExtractEntityId = defaultExtractEntityId
                , extractShardId: ShardRegion.ExtractShardId = defaultExtractShardId
                , role: Option[String] = None
               ): DefaultShardingService = DefaultShardingService(s, extractEntityId, extractShardId, role)

  }

}
