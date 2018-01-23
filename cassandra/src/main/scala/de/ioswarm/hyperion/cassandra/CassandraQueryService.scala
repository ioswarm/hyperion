package de.ioswarm.hyperion.cassandra

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Sink, Source}
import de.ioswarm.hyperion.PersistenceQueryService

abstract class CassandraQueryService extends PersistenceQueryService[CassandraReadJournal] {
  override def pluginId: String = CassandraReadJournal.Identifier
}

case class CassandraEventsByPersistenceId(name: String, sink: Sink[EventEnvelope, NotUsed]) extends CassandraQueryService {

  override def source(system: ActorSystem, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = journalReader(system).eventsByPersistenceId(name, fromSequenceNr, toSequenceNr)

}
