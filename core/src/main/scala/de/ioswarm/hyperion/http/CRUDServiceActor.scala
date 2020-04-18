package de.ioswarm.hyperion.http

import CRUD._
import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Source
import de.ioswarm.hyperion.{Service, ServiceActor}

import scala.concurrent.Future

abstract class CRUDServiceActor[L, R, E](service: CRUDService[L, R, E]) extends ServiceActor(service) {
  import akka.pattern.pipe
  import context.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()

  val eventReceiver: Option[ActorRef] = service.eventConsumer.map{ sink =>
    Source.actorRef(250, OverflowStrategy.dropNew)  // TODO configure buffersize and Overflowstrategy
      .to(sink)
      .run()
  }

  val childrefs: List[ActorRef] = service.children.map{ child =>
    child.run(serviceContext)
  }

  override def preStart(): Unit = {
    childrefs.foreach(context.watch)
    super.preStart()
  }

  def serviceReceive: Service.ServiceReceive = service.receive

  def crudReceive: Service.ServiceReceive = ctx => {
    case l: ListEntities[L, R, E] =>
      onList(ctx)(l)
        .map{ result =>
          result
        }
        .pipeTo(context.sender())
    case c: CreateEntity[L, R, E] =>
      onCreate(ctx)(c)
        .map{entity =>
          entity.foreach{ e => eventReceiver.foreach{ ref => ref ! EntityCreated(c.params, e, c.user)}}
          Result(entity)
        }
        .pipeTo(context.sender())
    case r: ReadEntity[L, R, E]   =>
      onRead(ctx)(r)
        .map{entity =>
          Result(entity)
        }
        .pipeTo(context.sender())
    case u: UpdateEntity[L, R, E] =>
      onRead(ctx)(ReadEntity(u.params, u.user)).flatMap {
        case Some(oldEntity) => onUpdate(ctx)(u).map(_.map(oldEntity -> _))
        case None => Future.successful(None)
      }.map{
        case Some((oe, ne)) =>
          eventReceiver.foreach(ref => ref ! EntityUpdated(u.params, oe, ne, u.user))
          Result(Some(ne))
        case None => Result(None)
      }.pipeTo(context.sender())
    case p: PatchEntity[L, R, E] =>
      onRead(ctx)(ReadEntity(p.params, p.user)).flatMap{
        case Some(oldEntity) => onPatch(ctx)(p).map(_.map(oldEntity -> _))
        case None => Future.successful(None)
      }.map{
        case Some((oe, ne)) =>
          eventReceiver.foreach(ref => ref ! EntityPatched(p.params, oe, ne, p.ops, p.user))
          Result(Some(ne))
        case None => Result(None)
      }.pipeTo(context.sender())
    case d: DeleteEntity[L, R, E] =>
      onRead(ctx)(ReadEntity(d.params, d.user)).flatMap{
        case Some(oldEntity) => onDelete(ctx)(d).map {
          case Some(l) if l > 0 => Some(oldEntity)
          case _ => None
        }
        case None => Future.successful(None)
      }.map{
        case Some(oe) =>
          eventReceiver.foreach(ref => ref ! EntityDeleted(d.params, oe, d.user))
          Result(Some(oe))
        case None => Result(None)
      }.pipeTo(context.sender())
  }

  override def receive: Receive = crudReceive(serviceContext) orElse serviceReceive(serviceContext)

  def onList: CRUDList[L, R, E]
  def onCreate: CRUDCreate[L, R, E]
  def onRead: CRUDRead[L, R, E]
  def onUpdate: CRUDUpdate[L, R, E]
  def onPatch: CRUDPatch[L, R, E]
  def onDelete: CRUDDelete[L, R, E]

}

final class DefaultCRUDServiceActor[L, R, E](service: CRUDService[L, R, E]) extends CRUDServiceActor(service) {

  def onList: CRUDList[L, R, E] = service.onList
  def onCreate: CRUDCreate[L, R, E] = service.onCreate
  def onRead: CRUDRead[L, R, E] = service.onRead
  def onUpdate: CRUDUpdate[L, R, E] = service.onUpdate
  def onPatch: CRUDPatch[L, R, E] = service.onPatch
  def onDelete: CRUDDelete[L, R, E] = service.onDelete

}
