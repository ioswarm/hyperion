package de.ioswarm.hyperion.cluster

import com.typesafe.config.{Config, ConfigFactory}
import de.ioswarm.hyperion.App

trait ClusterApp extends App {

  override def config(): Config = ConfigFactory.load("default-cluster")


}
