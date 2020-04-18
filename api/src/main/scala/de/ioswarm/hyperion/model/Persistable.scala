package de.ioswarm.hyperion.model

trait Persistable {

  def persistenceId: Option[String]

  def isDefined: Boolean = persistenceId.isDefined

}
