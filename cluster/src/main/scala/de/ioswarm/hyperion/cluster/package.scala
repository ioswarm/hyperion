package de.ioswarm.hyperion

import akka.cluster.sharding.ShardRegion
import de.ioswarm.hyperion.model.Command

package object cluster {

  val defaultExtractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id, cmd)
  }
  val defaultExtractShardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.id.hashCode) % 100).toString
  }

}
