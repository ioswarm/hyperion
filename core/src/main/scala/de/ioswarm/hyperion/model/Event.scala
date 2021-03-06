package de.ioswarm.hyperion.model

trait Event {

}

final case class FailureEvent(t: Throwable) extends Event

final case class Result(result: Any) extends Event {

  def as[T]: T = result.asInstanceOf[T]

}
