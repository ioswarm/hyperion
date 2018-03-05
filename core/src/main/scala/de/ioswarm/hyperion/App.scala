package de.ioswarm.hyperion

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait App {

  val appStart: Long = java.lang.System.currentTimeMillis()

  sys addShutdownHook shutdownApp()

  def shutdownApp(): Unit = {
    println(s"Ended after ${java.lang.System.currentTimeMillis()-appStart} ms.")
  }

  protected def commandLine: CommandLine = _cmdLine
  private var _cmdLine: CommandLine = _

  protected def internalConfig: Config = _internalConfig
  private var _internalConfig: Config = _

  protected def config: Config = _config
  private var _config: Config = _

  def options: Options = {
    new Options()
      .addOption("h", "help", false, "Print this help")
      .addOption("H", "hostname", true, "Hostname")
      .addOption("p", "port", true, "Port")
      .addOption("C", "cluster-hostname", true, "Cluster hostname")
      .addOption("c", "cluster-port", true, "Cluster port")
      .addOption("s", "seeds", true, "Comma separated list of seed-nodes")
      .addOption("n", "name", true, "Cluster-Name")
      .addOption("l", "library-path", true, "Library-Path (default ./lib)")
  }

  def hostname: String = if (commandLine.hasOption("hostname")) commandLine.getOptionValue("hostname") else internalConfig.getString("hyperion.http.hostname")
  def port: Int = {
    try {
      if (commandLine.hasOption("port")) commandLine.getOptionValue("port").toInt else internalConfig.getInt("hyperion.http.port")
    } catch {
      case e: Exception => 0
    }
  }
  def clusterHostname: String = if (commandLine.hasOption("cluster-hostname")) commandLine.getOptionValue("cluster-hostname") else internalConfig.getString("hyperion.cluster.hostname")
  def clusterPort: Int = {
    try {
      if (commandLine.hasOption("port")) commandLine.getOptionValue("port").toInt else internalConfig.getInt("hyperion.cluster.port")
    } catch {
      case e: Exception => 0
    }
  }
  def clusterName: String = if (commandLine.hasOption("name")) commandLine.getOptionValue("name") else "hyperion"

  def seeds: Array[String] = if (commandLine.hasOption("seeds")) commandLine.getOptionValue("seeds").split(",").map(_.trim) else internalConfig.getStringList("hyperion.cluster.seeds").toArray(Array.empty[String])

  def libraryPath: String = java.lang.System.getProperty("java.library.path", {
    val lpth = if (commandLine.hasOption("library-path")) commandLine.getOptionValue("library-path") else new File("./lib").getAbsolutePath
    java.lang.System.setProperty("java.library.path", lpth)
    println("LIBARAY-PATH: "+lpth)
    lpth
  })

  def startup(implicit system: ActorSystem): Seq[Service] = Seq.empty[Service]

  def main(args: Array[String]): Unit = {
    _cmdLine = new DefaultParser().parse(options, args)
    _internalConfig = ConfigFactory.load().withFallback(ConfigFactory.load("default-hyperion.conf"))

    if (commandLine.hasOption("h")) {
      new HelpFormatter().printHelp(clusterName, options)
      java.lang.System.exit(0)
    }



    _config = ConfigFactory.parseString(seeds
        .map(s => s"akka.tcp://$clusterName@$s")
        .mkString("akka.cluster.seed-nodes=[\"", "\", \"", "\"]"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$clusterHostname"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$clusterPort"))
      .withFallback(ConfigFactory.parseString(s"hyperion.http.hostname=$hostname"))
      .withFallback(ConfigFactory.parseString(s"hyperion.http.port=$port"))
      .withFallback(ConfigFactory.parseString(s"akka.cluster.metrics.native-library-extract-folder=$libraryPath"))
      .withFallback(ConfigFactory.load("default-hyperion.conf"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem(clusterName, config)

    implicit val hyperion: Hyperion = Hyperion(system)

    hyperion.start(startup(system) :_*)

    Await.result(hyperion.whenTerminated, Duration.Inf)
  }

}
