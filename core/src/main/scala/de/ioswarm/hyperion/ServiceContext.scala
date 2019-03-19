package de.ioswarm.hyperion

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import de.ioswarm.hyperion

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

private[hyperion] class ServiceContext(actorContext: ActorContext, actorLogger: LoggingAdapter) extends AkkaProvider {

  implicit def context: ActorContext = actorContext
  implicit def log: LoggingAdapter = actorLogger

  implicit def dispatcher: ExecutionContextExecutor = actorContext.dispatcher

  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()

  def config: Config = context.system.settings.config

  def system: ActorSystem = context.system

  lazy val settings: Hyperion.Settings = new hyperion.Hyperion.Settings(config)

  override def self: ActorRef = context.self

  protected val resolveTimeout: Timeout = Timeout(2.seconds)
  override lazy val hyperionRef: ActorRef = Await.result(system.actorSelection(system / "hyperion").resolveOne()(resolveTimeout), resolveTimeout.duration)

  override def actorOf(props: Props): ActorRef = context.actorOf(props)

  override def actorOf(props: Props, name: String): ActorRef = context.actorOf(props, name)


}
