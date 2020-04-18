package de.ioswarm.hyperion.cli

import com.mongodb.MongoWriteException
import de.ioswarm.hyperion.http.{ListOptions, ListResult}
import de.ioswarm.hyperion.{ServiceContext, http}
import de.ioswarm.hyperion.model.Persistable
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters

import scala.concurrent.Future
import scala.reflect.ClassTag

package object mongo {

  implicit class _mongoCliMongoCollectionExtender[CT :ClassTag](val collection: MongoCollection[CT]) {

    def create[T <: Persistable](entity: T)(implicit ctx: ServiceContext, classTag: ClassTag[T], encode: T => CT): Future[Option[T]] = {
      import ctx.dispatcher
      import ctx.log

      collection
        .insertOne(encode(entity))
        .toFutureOption()
        .recoverWith{
          case e: MongoWriteException =>
            log.warning(e.getMessage)
            Future.successful(None)
          case e: Throwable =>
            log.error(e, "Error while insert {} for '{}'", classTag.runtimeClass.getName, entity.persistenceId)
            Future.failed(e)
        }
        .map(_.map(_ => entity))
    }

    def read[T <: Persistable](id: String)(implicit ctx: ServiceContext, classTag: ClassTag[T], decode: CT => T): Future[Option[T]] = {
      import ctx.dispatcher
      import ctx.log

      collection
        .find(Filters.eq("_id", id))
        .toFuture()
        .recoverWith {
          case e: Throwable =>
            log.error(e, "Error while filter {} for _id: {}", classTag.runtimeClass.getName, id)
            Future.failed(e)
        }
        .map(_.headOption.map(decode))
    }

    def update[T <: Persistable](entity: T)(implicit ctx: ServiceContext, classTag: ClassTag[T], encode: T => CT): Future[Option[T]] = {
      import ctx.dispatcher
      import ctx.log

      collection
        .replaceOne(Filters.eq("_id", entity.persistenceId.get), entity)
        .toFutureOption()
        .recoverWith {
          case e: Throwable =>
            log.error(e, "Error while update {}} for _id: {}", classTag.runtimeClass.getName, entity.persistenceId)
            Future.failed(e)
        }
        .map(_.map(_ => entity))
    }

    def delete(id: String)(implicit ctx: ServiceContext): Future[Option[Long]] = {
      import ctx.dispatcher
      import ctx.log

      collection
        .deleteOne(Filters.eq("_id", id))
        .toFutureOption()
        .recoverWith {
          case e: Throwable =>
            log.error(e, "Error while delete _id: {}", id)
            Future.failed(e)
        }
        .map(_.find(_.getDeletedCount > 0L).map(_.getDeletedCount))
    }

    def count(implicit ctx: ServiceContext): Future[Long] = {
      import ctx.dispatcher
      import ctx.log

      collection
        .countDocuments()
        .toFutureOption()
        .recoverWith{
          case e: Throwable =>
            log.error(e, "Error while determine count")
            Future.failed(e)
        }
        .map(_.getOrElse(0))
    }

    def filter[T <: Persistable](from: Long, size: Option[Long], filter: Option[http.Filter.Condition])(implicit ctx: ServiceContext, classTag: ClassTag[T], decode: CT => T): Future[Vector[T]] = {
      import ctx.dispatcher
      import ctx.log
      import Filter._

      filter.map(cond => cond.parseAsBson).map{
        case Left(msg) => Left(msg)
        case Right(bson) => Right(collection.find(bson))
      }.getOrElse(Right(collection.find())) match {
        case Right(result) =>
          result
            .skip(from.toInt)
            .limit(size.map(_.toInt).getOrElse(Int.MaxValue))
            .toFuture()
            .recoverWith{
              case e: Throwable =>
                log.error(e, "Error while fetch all {}", classTag.runtimeClass.getName)
                Future.failed(e)
            }
            .map(_.map(decode).toVector)
        case Left(msg) =>
          log.warning(msg)
          Future.failed(new Exception(msg))
      }
    }

    def list[T <: Persistable](listOptions: ListOptions)(implicit ctx: ServiceContext, classTag: ClassTag[T], decode: CT => T): Future[ListResult[T]] = {
      import ctx.dispatcher
      import ctx.log

      listOptions.filter.map(http.Filter.Interpreter.interpret).map(_.map(Some(_))).getOrElse(Right(None)) match {
        case Right(ocond) => for {
          cnt <- count
          result <- filter(listOptions.from, listOptions.size, ocond)
        } yield ListResult(listOptions.from, Some(cnt), result)
        case Left(msg) =>
          log.warning(msg)
          Future.failed(new Exception(msg))
      }
    }

  }

}
