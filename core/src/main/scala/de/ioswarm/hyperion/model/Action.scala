package de.ioswarm.hyperion.model

import akka.Done
import akka.persistence.journal.Tagged

import scala.concurrent.Future

abstract class Action[+T] extends Product with Serializable {
  def value: T
  def tags: Set[String]
  def isPersistable: Boolean
  def isReplyable: Boolean
  def isTagged: Boolean = tags.nonEmpty

  def taggedValue: Tagged = Tagged(value, if (isTagged) tags else Set(this.getClass.getSimpleName))

  def success: Future[Action[T]] = Future.successful(this)
}

case object Passive extends Action[Done] {
  override def value: Done = Done

  override def tags: Set[String] = Set.empty[String]

  override def isPersistable: Boolean = false

  override def isReplyable: Boolean = false
}

final case class PersistAction[+T](value: T, args: String*) extends Action[T] {
  def tags: Set[String] = args.toSet

  def isPersistable: Boolean = true

  def isReplyable: Boolean = false
}

final case class ReplyAction[+T](value: T) extends Action[T] {
  def tags: Set[String] = Set.empty[String]

  def isPersistable: Boolean = false

  def isReplyable: Boolean = true
}

final case class PersistAndReplyAction[+T](value: T, args: String*) extends Action[T] {
  def tags: Set[String] = args.toSet

  def isPersistable: Boolean = true

  def isReplyable: Boolean = true
}
