package de.ioswarm.hyperion.cassandra

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.{Sink, Source}
import de.ioswarm.hyperion.QueryStream

object CassandraQueryStreamSupport {



  def eventsByPersistenceId(f: ActorSystem => Sink[EventEnvelope, NotUsed]): QueryStream  = new CassandraQueryStream(f) {

    override def source(id: String, fromSeq: Long, toSeq: Long, system: ActorSystem): Source[EventEnvelope, NotUsed] = journal(system).eventsByPersistenceId(id, fromSeq, toSeq)

  }


  sealed abstract class CassandraQueryStream(f: ActorSystem => Sink[EventEnvelope, NotUsed]) extends QueryStream {

    def sink(system: ActorSystem): Sink[EventEnvelope, NotUsed] = f(system)

    def journal(system: ActorSystem): CassandraReadJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  }

}
