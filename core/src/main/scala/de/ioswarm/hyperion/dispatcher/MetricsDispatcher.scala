package de.ioswarm.hyperion.dispatcher

import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension}
import de.ioswarm.hyperion.{Service, ServiceActor}

object MetricsDispatcher {

  def apply(): Service = new Service {

    override def name: String = "metrics"

    override def props: Props = Props[MetricsDispatcher]
  }

}
class MetricsDispatcher extends ServiceActor {

  private val cluster = Cluster(context.system)
  private val address = cluster.selfAddress


  val metricsExt = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = {
    metricsExt.subscribe(self)
  }

  override def postStop(): Unit = {
    metricsExt.unsubscribe(self)
  }

  override def serviceReceive: Receive = {
    case ClusterMetricsChanged(metrics) =>
      log.info(metrics.toString())
  }

}
