package de.ioswarm.hyperion.http

import argonaut._
import Argonaut._
import de.ioswarm.hyperion.http.Patch.PatchOperator

final case class Result[E](entity: Option[E])


object ListOptions {

  implicit def _httpListOptionsEncode: EncodeJson[ListOptions] = EncodeJson{ lo =>
    ("from" := lo.from) ->:
      ("size" :=? lo.size) ->?:
      ("filter" :=? lo.filter) ->?:
      ("attributes" :=? (if (lo.attributes.isEmpty) None else Some(lo.attributes))) ->?:
    jEmptyObject
  }

  implicit def _httpListOptionsDecode: DecodeJson[ListOptions] = DecodeJson{ c => for {
    from <- (c --\ "from").as[Long]
    size <- (c --\ "size").as[Option[Long]]
    filter <- (c --\ "filter").as[Option[String]]
    attributes <- (c --\ "attributes").as[Option[List[String]]]
  } yield ListOptions(
    from
    , size
    , filter
    , attributes.getOrElse(List.empty)
  )}

}
final case class ListOptions(from: Long, size: Option[Long], filter: Option[String], attributes: List[String])


object ListResult {

  implicit def _httpListResultEncode[E](implicit ee: EncodeJson[E]): EncodeJson[ListResult[E]] = EncodeJson{ lr =>
    ("from" := lr.from) ->:
      ("size" := lr.size) ->:
      ("count" :=? lr.count) ->?:
      ("result" := lr.result) ->:
    jEmptyObject
  }

  implicit def _httpListResultDecode[E](implicit ed: DecodeJson[E]): DecodeJson[ListResult[E]] = DecodeJson{ c => for{
    from <- (c --\ "from").as[Long]
    count <- (c --\ "count").as[Option[Long]]
    result <- (c --\ "result").as[Vector[E]]
  } yield ListResult(
    from
    , count
    , result
  )}

}
final case class ListResult[E](from: Long, count: Option[Long], result: Vector[E]) {
  def size: Long = result.size

  def map[K](f: E => K): ListResult[K] = ListResult(from, count, result.map(f))
}

object Patch {

  sealed abstract class PatchOperator(val name: String)
  object PatchOperator {
    case object ADD extends PatchOperator("add")
    case object REPLACE extends PatchOperator("replace")
    case object REMOVE extends PatchOperator("remove")
  }

  implicit def _httpPatchOperatorEncode: EncodeJson[PatchOperator] = EncodeJson{ po => jString(po.name) }
  implicit def _httpPatchOperatorDecode: DecodeJson[PatchOperator] = implicitly[DecodeJson[String]].map{
    case "replace" => PatchOperator.REPLACE
    case "remove" => PatchOperator.REMOVE
    case _ => PatchOperator.ADD
  }

  implicit def _httpPatchEncode: EncodeJson[Patch] = EncodeJson{ p =>
    ("op" := p.operator) ->:
      ("path" :=? p.path) ->?:
      ("value" :=? p.value) ->?:
    jEmptyObject
  }

  implicit def _httpPatchDecode: DecodeJson[Patch] = DecodeJson{ c => for {
    operator <- (c --\ "op").as[PatchOperator]
    path <- (c --\ "path").as[Option[String]]
    value <- (c --\ "value").as[Option[Json]]
  } yield Patch(
    operator
    , path
    , value
  )}

}
final case class Patch(operator: PatchOperator, path: Option[String], value: Option[Json])

object PatchOperations {

  implicit def _httpPatchOperationsCodec: CodecJson[PatchOperations] = casecodec1(PatchOperations.apply, PatchOperations.unapply)(
    "operations"
  )

}
final case class PatchOperations(operations: List[Patch])