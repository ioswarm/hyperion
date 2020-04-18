package de.ioswarm.hyperion.cli.mongo

import com.typesafe.config.{Config, ConfigFactory}
import org.mongodb.scala.{MongoClient, MongoDatabase}

object MongoCli {

  private lazy val config: Config = ConfigFactory.load().getConfig("mongo")
  lazy val client: MongoClient = MongoClient(config.getString("uri"))
  lazy val database: MongoDatabase = client.getDatabase(config.getString("database"))

}
