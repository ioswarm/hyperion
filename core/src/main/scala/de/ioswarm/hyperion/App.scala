package de.ioswarm.hyperion

import akka.actor.{ActorSystem, Terminated}
import com.typesafe.config.{Config, ConfigFactory}

trait App extends CoreImplicits
  with ServiceBuilderImplicits {

  val appStartedAt: Long = System.currentTimeMillis()

  println("\033[0;31m" +
    """
      |    __                          _
      |   / /_  __  ______  ___  _____(_)___  ____
      |  / __ \/ / / / __ \/ _ \/ ___/ / __ \/ __ \
      | / / / / /_/ / /_/ /  __/ /  / / /_/ / / / /
      |/_/ /_/\__, / .___/\___/_/  /_/\____/_/ /_/
      |      /____/_/
      """.stripMargin + "\033[0m")

  sys addShutdownHook shutdownApp()

  def config(): Config = ConfigFactory.load()

  implicit val hyperion: Hyperion = Hyperion(config())
  val _name: String = hyperion.settings.systemName

  protected def args: Array[String] = _args
  private var _args: Array[String] = _

  def shutdownApp(): Unit = {
    println(s"${_name} ended after ${System.currentTimeMillis()-appStartedAt} ms")
  }

  def systemName(): String = _name

  implicit val actorSystem: ActorSystem = hyperion.system

  def main(args: Array[String]): Unit = {
    _args = args
  }

  def await(): Terminated = hyperion.await()

}
