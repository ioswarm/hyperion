package de.ioswarm.hyperion.dispatcher

import akka.actor.{Address, Props}
import akka.cluster.Cluster
import akka.event.EventStream
import de.ioswarm.hyperion.model.{LogEntry, LogEvent}
import de.ioswarm.hyperion.{Service, ServiceActor}

class LoggingDispatcher() extends Service {

  override def name: String = "logging"

  override def props: Props = Props[LoggingDispatcherService]

}
class LoggingDispatcherService extends ServiceActor {

  val events: EventStream = context.system.eventStream
  val cluster = Cluster(context.system)
  val address: Address = cluster.selfAddress
  val systemName: String = context.system.name

  override def preStart(): Unit = {
    events.subscribe(self, classOf[LogEntry])
  }

  override def postStop(): Unit = {
    events.unsubscribe(self)
  }

  override def serviceReceive: Receive = {
    case e: LogEntry =>
      events.publish(LogEvent.create(e, Some(systemName), Some(address.toString)))
  }

}
