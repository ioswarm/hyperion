package de.ioswarm.hyperion.http

import CRUD._
import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Source
import de.ioswarm.hyperion.{ServiceActor, Service}

abstract class CRUDServiceActor[L, R, E](service: CRUDService[L, R, E]) extends ServiceActor(service) {
  import akka.pattern.pipe
  import context.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()

  val eventReceiver: Option[ActorRef] = service.eventConsumer.map{ sink =>
    Source.actorRef(25, OverflowStrategy.fail)  // TODO configure buffersize and Overflowstrategy
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
          entity.foreach{ e => eventReceiver.foreach{ ref => ref ! EntityRead(r.params, e, r.user)}}
          Result(entity)
        }
        .pipeTo(context.sender())
    case u: UpdateEntity[L, R, E] =>
      onUpdate(ctx)(u)
        .map{entity =>
          entity.foreach{ e => eventReceiver.foreach{ ref => ref ! EntityUpdated(u.params, u.oldEntity, e, u.user)}}
          Result(entity)
        }
        .pipeTo(context.sender())
    case d: DeleteEntity[L, R, E] =>
      onDelete(ctx)(d)
        .map{entity =>
          entity.foreach{ e => eventReceiver.foreach{ ref => ref ! EntityCreated(d.params, e, d.user)}}
          Result(entity)
        }
        .pipeTo(context.sender())
  }

  override def receive: Receive = crudReceive(serviceContext) orElse serviceReceive(serviceContext)

  def onCreate: CRUDCreate[L, R, E]
  def onRead: CRUDRead[L, R, E]
  def onUpdate: CRUDUpdate[L, R, E]
  def onDelete: CRUDDelete[L, R, E]

}

final class DefaultCRUDServiceActor[L, R, E](service: CRUDService[L, R, E]) extends CRUDServiceActor(service) {

  def onCreate: CRUDCreate[L, R, E] = service.onCreate
  def onRead: CRUDRead[L, R, E] = service.onRead
  def onUpdate: CRUDUpdate[L, R, E] = service.onUpdate
  def onDelete: CRUDDelete[L, R, E] = service.onDelete

}
