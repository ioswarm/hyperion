package de.ioswarm.hyperion.model

import de.ioswarm.time.DateTime

trait Message {

  def timestamp: DateTime
  def subject: String
  def message: Option[String]
  def meta: Map[String, String]
  def tags: Set[String]

}
