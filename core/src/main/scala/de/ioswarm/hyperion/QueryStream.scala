package de.ioswarm.hyperion

import akka.NotUsed
import akka.actor.{ActorContext, ActorSystem}
import akka.persistence.query.EventEnvelope
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import akka.stream.scaladsl.{Keep, Sink, Source}

trait QueryStream {

  def source(id: String, fromSeq: Long, toSeq: Long, system: ActorSystem): Source[EventEnvelope, NotUsed]

  def sink: Sink[EventEnvelope, Any]

  def run(id: String, fromSeq: Long, toSeq: Long)(implicit mat: Materializer, context: ActorContext): KillSwitch = source(id, fromSeq, toSeq, context.system)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(sink)(Keep.left)
    .run()



}
