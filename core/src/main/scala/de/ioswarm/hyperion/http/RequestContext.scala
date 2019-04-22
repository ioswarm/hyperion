package de.ioswarm.hyperion.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.{RequestContext => HttpRequestContext}
import akka.http.scaladsl.model._
import akka.util.Timeout
import com.typesafe.config.Config
import de.ioswarm.hyperion
import de.ioswarm.hyperion.{AkkaProvider, Hyperion}

import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.reflect.ClassTag

class RequestContext(val requestContext: HttpRequestContext, val system: ActorSystem) extends AkkaProvider with Completes {
  import concurrent.duration._

  override def config: Config = system.settings.config

  override def settings: Hyperion.Settings = new hyperion.Hyperion.Settings(config)

  override def log: LoggingAdapter = requestContext.log

  override def self: ActorRef = hyperionRef

  override def sender(): ActorRef = self

  protected val resolveTimeout: Timeout = Timeout(2.seconds)
  override lazy val hyperionRef: ActorRef = Await.result(system.actorSelection(system / "hyperion").resolveOne()(resolveTimeout), resolveTimeout.duration)

  override implicit def dispatcher: ExecutionContextExecutor = requestContext.executionContext

  override def actorOf(props: Props): ActorRef = system.actorOf(props)

  override def actorOf(props: Props, name: String): ActorRef = system.actorOf(props, name)

  def request: HttpRequest = requestContext.request

  def headers: immutable.Seq[HttpHeader] = request.headers
  def header[T >: Null <: HttpHeader](implicit clazzTag: ClassTag[T]): Option[T] = request.header

  def entity: HttpEntity = request.entity

  def method: HttpMethod = request.method

  def protocol: HttpProtocol = request.protocol

  def uri: Uri = request.uri

}
