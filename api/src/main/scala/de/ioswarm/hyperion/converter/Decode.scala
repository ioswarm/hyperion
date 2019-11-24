package de.ioswarm.hyperion.converter

trait Decode[S, T] {

  @throws[Throwable]
  def decode(target: T): S

  def decodeEither(target: T): Either[Throwable, S] = try {
    Right(decode(target))
  } catch {
    case t: Throwable => Left(t)
  }

  def decodeOption(target: T): Option[S] = decodeEither(target) match {
    case Right(s) => Some(s)
    case Left(_) => None
  }

}
