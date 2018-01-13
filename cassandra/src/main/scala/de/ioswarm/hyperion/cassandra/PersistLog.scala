package de.ioswarm.hyperion.cassandra

import akka.actor.Props
import akka.event.EventStream
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import de.ioswarm.cassie.Cluster
import de.ioswarm.cassie.akka.CassieTableSink
import de.ioswarm.hyperion.cassandra.model.HyperionFormats
import de.ioswarm.hyperion.model.LogEvent
import de.ioswarm.hyperion.{Service, ServiceActor}

class PersistLog() extends Service {
  override def name: String = "cassandra-persist-log"

  override def props: Props = Props[PersistLogService]
}

class PersistLogService extends ServiceActor with HyperionFormats {

  import context.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val con = Cluster(config.getString("hyperion.cassandra.keyspace"))

  val events: EventStream = context.system.eventStream

  override def preStart(): Unit = {
    events.subscribe(self, classOf[LogEvent])
  }

  override def postStop(): Unit = {
    events.unsubscribe(self)
  }

  val queue: SourceQueueWithComplete[LogEvent] = Source.queue[LogEvent](Int.MaxValue, OverflowStrategy.backpressure).to(CassieTableSink(10)).run()

  override def serviceReceive: Receive = {
    case e: LogEvent => queue.offer(e)
  }



}
