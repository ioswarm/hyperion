package de.ioswarm.hyperion.model

import java.time.LocalDateTime

trait Message {

  def time: LocalDateTime
  def message: Any
  def meta: Map[String, Any]
  def tags: Set[String]

}
