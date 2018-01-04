package de.ioswarm.hyperion

import com.typesafe.config.Config

object System {

  def apply(config: Config): ActorService = {
    import scala.collection.JavaConverters._

    ActorServiceImpl(
      "system"
      , actorClass = classOf[System]
      , children = config.getStringList("extensions").asScala.map(cn => Class.forName(cn).newInstance().asInstanceOf[Service])
    )
  }

}
class System(service: ActorService) extends ActorServiceActor(service) {

}
