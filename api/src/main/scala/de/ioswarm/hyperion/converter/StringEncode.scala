package de.ioswarm.hyperion.converter

import java.util.UUID

trait StringEncode[T] extends Encode[String, T]

object StringEncoder {

  implicit val stringToInt: StringEncode[Int] = _.toInt

  implicit val stringToLong: StringEncode[Long] = _.toLong

  implicit val stringToByte: StringEncode[Byte] = _.toByte

  implicit val stringToShort: StringEncode[Short] = _.toShort

  implicit val stringToDouble: StringEncode[Double] = _.toDouble

  implicit val stringToFloat: StringEncode[Float] = _.toFloat

  implicit val stringToBoolean: StringEncode[Boolean] = _.toBoolean

  implicit val stringToUUID: StringEncode[UUID] = UUID.fromString(_)

}