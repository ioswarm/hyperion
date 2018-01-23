package de.ioswarm.hyperion.model

trait Command {
  def id: String
}

final case class Get(id: String) extends Command
