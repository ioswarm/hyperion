package de.ioswarm.hyperion.model

trait AuthenticatedUser {

  def username: String
  def meta: Map[String, String]

}
