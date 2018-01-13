package de.ioswarm.hyperion.cassandra

import akka.actor.Props
import akka.event.EventStream
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import de.ioswarm.cassie.Cluster
import de.ioswarm.cassie.akka.CassieTableSink
import de.ioswarm.hyperion.{Service, ServiceActor}
import de.ioswarm.hyperion.cassandra.model.HyperionFormats
import de.ioswarm.hyperion.model.MetricEvent

class PersistMetric() extends Service {

  override def name: String = "cassandra-persist-metric"

  override def props: Props = Props[PersistMetricService]

}

class PersistMetricService extends ServiceActor with HyperionFormats {

  import context.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val con = Cluster(config.getString("hyperion.cassandra.keyspace"))

  val events: EventStream = context.system.eventStream

  override def preStart(): Unit = {
    events.subscribe(self, classOf[MetricEvent])
  }

  override def postStop(): Unit = {
    events.unsubscribe(self)
  }

  val queue: SourceQueueWithComplete[MetricEvent] = Source.queue[MetricEvent](Int.MaxValue, OverflowStrategy.backpressure).to(CassieTableSink(10)).run()

  override def serviceReceive: Receive = {
    case e: MetricEvent => queue.offer(e)
  }

}