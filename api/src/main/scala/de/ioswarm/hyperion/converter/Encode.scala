package de.ioswarm.hyperion.converter

trait Encode[S, T] {

  @throws[Throwable]
  def encode(source: S): T

  def encodeEither(source: S): Either[Throwable, T] = try {
    Right(encode(source))
  } catch {
    case t: Throwable => Left(t)
  }

  def encodeOption(source: S): Option[T] = encodeEither(source) match {
    case Right(v) => Some(v)
    case Left(_) => None
  }

}
