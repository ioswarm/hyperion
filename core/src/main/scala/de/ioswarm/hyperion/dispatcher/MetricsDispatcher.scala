package de.ioswarm.hyperion.dispatcher

import java.time.{LocalDateTime, ZoneId}

import akka.actor.{Address, Props}
import akka.cluster.Cluster
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension}
import akka.event.EventStream
import de.ioswarm.hyperion.model.MetricEvent
import de.ioswarm.hyperion.{Service, ServiceActor}

class MetricsDispatcher() extends Service {

  override def name: String = "metrics"

  override def props: Props = Props[MetricsDispatcherService]

}
class MetricsDispatcherService extends ServiceActor {

  val events: EventStream = context.system.eventStream
  val cluster = Cluster(context.system)
  val address: Address = cluster.selfAddress
  val systemName: String = context.system.name


  val metricsExt = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = {
    metricsExt.subscribe(self)
  }

  override def postStop(): Unit = {
    metricsExt.unsubscribe(self)
  }

  override def serviceReceive: Receive = {
    case ClusterMetricsChanged(metrics) =>
      metrics.filter(_.address == address) foreach { nodeMetrics =>
        val evt = MetricEvent(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant
          , nodeMetrics.metric("heap-memory-used").map(_.value.doubleValue())
          , nodeMetrics.metric("heap-memory-max").map(_.value.doubleValue())
          , nodeMetrics.metric("cpu-combined").map(_.value.doubleValue())
          , nodeMetrics.metric("processors").map(_.value.doubleValue())
          , nodeMetrics.metric("system-load-average").map(_.value.doubleValue())
          , nodeMetrics.metric("heap-memory-committed").map(_.value.doubleValue())
          , nodeMetrics.metric("cpu-stolen").map(_.value.doubleValue())
          , Option(systemName)
          , Option(nodeMetrics.address.toString))
        events.publish(evt)
      }
  }

}
