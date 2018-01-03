package de.ioswarm.hyperion.json

import java.time.Instant

import argonaut._
import Argonaut._

object Codecs {

  implicit def instantEncoder: EncodeJson[Instant] = EncodeJson(d =>
    jString(d.toString)
  )

  implicit def instantDecoder: DecodeJson[Instant] = implicitly[DecodeJson[String]].map(s =>
    Instant.parse(s)
  )

}
