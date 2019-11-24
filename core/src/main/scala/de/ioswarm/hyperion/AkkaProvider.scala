package de.ioswarm.hyperion

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import de.ioswarm.hyperion.Hyperion.Settings

import scala.concurrent.ExecutionContextExecutor

trait AkkaProvider {

  def config: Config
  def system: ActorSystem

  def settings: Settings

  def log: LoggingAdapter

  def self: ActorRef

  def sender(): ActorRef

  def hyperionRef: ActorRef

  implicit def dispatcher: ExecutionContextExecutor

  def actorOf(props: Props): ActorRef

  def actorOf(props: Props, name: String): ActorRef

  def actorOf(service: Service): ActorRef = {
    /*import Hyperion._

    val ref = actorOf(service.props, service.name)
    if (service.hasRoute) hyperionRef ! HttpAppendRoute(service.route(ref))
    ref*/
    service.run(this)
  }

}
