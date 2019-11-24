package de.ioswarm.hyperion.model

import akka.Done
import de.ioswarm.hyperion.AkkaProvider

import scala.collection.immutable

object Action {

  def apply(command: Command): Action = Action(command, immutable.Seq.empty, false, noReply)
  def apply(command: Command, event: Event): Action = Action(command, immutable.Seq(event), false, noReply)

  type CommandReply = (AkkaProvider, Command, immutable.Seq[Event], Any) => Unit

  object noReply extends CommandReply {
    override def apply(v1: AkkaProvider, v2: Command, v3: immutable.Seq[Event], v4: Any): Unit = ()
  }

  object doneReply extends CommandReply {
    override def apply(v1: AkkaProvider, v2: Command, v3: immutable.Seq[Event], v4: Any): Unit = v1.sender() ! Done
  }

  object valueReply extends CommandReply {
    override def apply(v1: AkkaProvider, v2: Command, v3: immutable.Seq[Event], v4: Any): Unit = v1.sender() ! v4
  }

  def reply(a: Any): CommandReply = { (ctx, _, _, _) =>
    ctx.sender ! a
  }

}

import de.ioswarm.hyperion.model.Action._

case class Action(command: Command, events: immutable.Seq[Event], isPersistable: Boolean, onReply: CommandReply) {

  def nonEmpty: Boolean = events.nonEmpty
  def isEmpty: Boolean = events.isEmpty

  def withEvent(evt: Event): Action = copy(events = this.events :+ evt)
  def withEvent(f: Command => Event): Action = withEvent(f(command))

  def persist(evt: Event): Action = withEvent(evt).asPersistable
  def persist(f: Command => Event): Action = withEvent(f).asPersistable

  def withPersistableEvent(e: Event): Action = withEvent(e).asPersistable
  def withPersistableEvent(f: Command => Event): Action = withEvent(f).asPersistable

  def withReply(f: CommandReply): Action = copy(onReply = f)
  def replyWith(a: Any): Action = withReply(Action.reply(a))

  def persistable(persistable: Boolean): Action = copy(isPersistable = persistable)

  def asPersistable: Action = persistable(true)

}
