package de.ioswarm.hyperion.json

import argonaut._
import Argonaut._
import de.ioswarm.hyperion.http.{Filter, Patch}
import de.ioswarm.time.{Date, DateTime, Time}

object JsonUtils {

  private val rlong = "^[0-9]+$"
  private val rdouble = "^[0-9]+\\.[0-9]+$"
  private val rboolean = "^(true|false)$"
  private val rdate = "^([0-9]{4})\\-([1][0-2]|[0][1-9])\\-([3][0-1]|[1-2][0-9]|[0][1-9])$"
  private val rtime = "^(([2][0-3]|[0-1][0-9]):([0-5][0-9])(:([0-5][0-9])(\\.([0-9]{1,3}))?)?)$"
  private val rdatetime = "^([0-9]{4})\\-([1][0-2]|[0][1-9])\\-([3][0-1]|[1-2][0-9]|[0][1-9])(\\s|T)(([2][0-3]|[0-1][0-9]):([0-5][0-9])(:([0-5][0-9])(\\.([0-9]{1,3}))?)?)((Z)|(([+,-]([1][0-4]|0[0-9])):([0-5][0-9])))?$"

  def asString(j: Json): Option[String] = j.as[String].toOption
  def asLong(j: Json): Option[Long] = j.as[Long].toOption
  def asDouble(j: Json): Option[Double] = j.as[Double].toOption
  def asBoolean(j: Json): Option[Boolean] = j.as[Boolean].toOption
  def asDate(j: Json): Option[Date] = asString(j).map(Date(_))
  def asTime(j: Json): Option[Time] = asString(j).map(Time(_))
  def asDateTime(j: Json): Option[DateTime] = asString(j).map(DateTime(_))

  def isLong(value: String): Boolean = value.matches(rlong)
  def isDouble(value: String): Boolean = value.matches(rdouble)
  def isDate(value: String): Boolean = value.matches(rdate)
  def isTime(value: String): Boolean = value.matches(rtime)
  def isDateTime(value: String): Boolean = value.matches(rdatetime)

  def stringValueToJson(value: String): Json = value match {
    case v if v.startsWith("\"") || v.endsWith("\"") => jString(v.replace("\"", ""))
    case v if v.toLowerCase == "true" || v.toLowerCase == "false" => jBool(v.toBoolean)
    case v if v.matches(rdouble) => jNumber(v.toDouble)
    case v if v.matches(rlong) => jNumber(v.toLong) // TODO ... check if Int
    case v => jString(v)
  }

  def isEqual(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if isDateTime(ls) && isDateTime(rs) => DateTime(ls) == DateTime(rs)
      case (Some(ls), Some(rs)) if isDate(ls) && isDate(rs) => Date(ls) == Date(rs)
      case (Some(ls), Some(rs)) if isTime(ls) && isTime(rs) => Time(ls) == Time(rs)
      case (Some(ls), Some(rs)) => ls == rs
      case (None, None) => true
      case _ => false
    }
    case (l, r) if l.isBool && r.isBool => asBoolean(l) == asBoolean(r)
    case (l, r) if l.isNumber && r.isNumber => (l.toString(), r.toString()) match {
      case (ls, rs) if isDouble(ls) && isDouble(rs) => asDouble(l) == asDouble(r)
      case (ls, rs) if isLong(ls) && isLong(rs) => asLong(l) == asLong(r)
      case _ => false
    }
    case (l, r) => l == r
  }

  def isNotEqual(left: Json, right: Json): Boolean = !isEqual(left, right)

  def contains(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if rs.nonEmpty => ls.contains(rs)
      case _ => false
    }
    case _ => false
  }

  def startWith(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if rs.nonEmpty => ls.startsWith(rs)
      case _ => false
    }
    case _ => false
  }

  def endsWith(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if rs.nonEmpty => ls.endsWith(rs)
      case _ => false
    }
    case _ => false
  }

  def isGreater(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if isDateTime(ls) && isDateTime(rs) => DateTime(ls) > DateTime(rs)
      case (Some(ls), Some(rs)) if isDate(ls) && isDate(rs) => Date(ls) > Date(rs)
      case (Some(ls), Some(rs)) if isTime(ls) && isTime(rs) => Time(ls) > Time(rs)
      case (Some(ls), Some(rs)) => ls > rs
      case (None, None) => false
      case _ => false
    }
    case (l, r) if l.isBool && r.isBool => false
    case (l, r) if l.isNumber && r.isNumber => (l.toString(), r.toString()) match {
      case (ls, rs) if isDouble(ls) && isDouble(rs) => (asDouble(l), asDouble(r)) match {
        case (Some(ld), Some(rd)) => ld > rd
        case _ => false
      }
      case (ls, rs) if isLong(ls) && isLong(rs) => (asLong(l), asLong(r)) match {
        case (Some(ll), Some(rl)) => ll > rl
        case _ => false
      }
      case _ => false
    }
    case (l, r) => false
  }

  def isGreaterOrEqual(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if isDateTime(ls) && isDateTime(rs) => DateTime(ls) >= DateTime(rs)
      case (Some(ls), Some(rs)) if isDate(ls) && isDate(rs) => Date(ls) >= Date(rs)
      case (Some(ls), Some(rs)) if isTime(ls) && isTime(rs) => Time(ls) >= Time(rs)
      case (Some(ls), Some(rs)) => ls >= rs
      case (None, None) => false
      case _ => false
    }
    case (l, r) if l.isBool && r.isBool => false
    case (l, r) if l.isNumber && r.isNumber => (l.toString(), r.toString()) match {
      case (ls, rs) if isDouble(ls) && isDouble(rs) => (asDouble(l), asDouble(r)) match {
        case (Some(ld), Some(rd)) => ld >= rd
        case _ => false
      }
      case (ls, rs) if isLong(ls) && isLong(rs) => (asLong(l), asLong(r)) match {
        case (Some(ll), Some(rl)) => ll >= rl
        case _ => false
      }
      case _ => false
    }
    case (l, r) => false
  }

  def isLess(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if isDateTime(ls) && isDateTime(rs) => DateTime(ls) < DateTime(rs)
      case (Some(ls), Some(rs)) if isDate(ls) && isDate(rs) => Date(ls) < Date(rs)
      case (Some(ls), Some(rs)) if isTime(ls) && isTime(rs) => Time(ls) < Time(rs)
      case (Some(ls), Some(rs)) => ls < rs
      case (None, None) => false
      case _ => false
    }
    case (l, r) if l.isBool && r.isBool => false
    case (l, r) if l.isNumber && r.isNumber => (l.toString(), r.toString()) match {
      case (ls, rs) if isDouble(ls) && isDouble(rs) => (asDouble(l), asDouble(r)) match {
        case (Some(ld), Some(rd)) => ld < rd
        case _ => false
      }
      case (ls, rs) if isLong(ls) && isLong(rs) => (asLong(l), asLong(r)) match {
        case (Some(ll), Some(rl)) => ll < rl
        case _ => false
      }
      case _ => false
    }
    case (l, r) => false
  }

  def isLessOrEqual(left: Json, right: Json): Boolean = (left, right) match {
    case (l, r) if l.isString && r.isString => (asString(l), asString(r)) match {
      case (Some(ls), Some(rs)) if isDateTime(ls) && isDateTime(rs) => DateTime(ls) <= DateTime(rs)
      case (Some(ls), Some(rs)) if isDate(ls) && isDate(rs) => Date(ls) <= Date(rs)
      case (Some(ls), Some(rs)) if isTime(ls) && isTime(rs) => Time(ls) <= Time(rs)
      case (Some(ls), Some(rs)) => ls <= rs
      case (None, None) => false
      case _ => false
    }
    case (l, r) if l.isBool && r.isBool => false
    case (l, r) if l.isNumber && r.isNumber => (l.toString(), r.toString()) match {
      case (ls, rs) if isDouble(ls) && isDouble(rs) => (asDouble(l), asDouble(r)) match {
        case (Some(ld), Some(rd)) => ld <= rd
        case _ => false
      }
      case (ls, rs) if isLong(ls) && isLong(rs) => (asLong(l), asLong(r)) match {
        case (Some(ll), Some(rl)) => ll <= rl
        case _ => false
      }
      case _ => false
    }
    case (l, r) => false
  }

  def verify(json: Json, condition: Filter.Condition): Boolean = condition match {
    case _ if !json.isObject => false
    case Filter.AttributeCondition(attr, op, value) => op match {
      case Filter.Operator.EQ => value.exists(v => isEqual(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.NE => value.exists(v => isNotEqual(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.CO => value.exists(v => contains(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.SW => value.exists(v => startWith(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.EW => value.exists(v => endsWith(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.PR => json.field(attr).nonEmpty
      case Filter.Operator.GT => value.exists(v => isGreater(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.GE => value.exists(v => isGreaterOrEqual(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.LT => value.exists(v => isLess(json.fieldOrNull(attr), stringValueToJson(v)))
      case Filter.Operator.LE => value.exists(v => isLessOrEqual(json.fieldOrNull(attr), stringValueToJson(v)))
      case _ => false
    }
    case Filter.CompoundCondition(lcond, lo, rcond) => lo match {
      case Filter.Logical.AND => verify(json, lcond) && verify(json, rcond)
      case Filter.Logical.OR => verify(json, lcond) || verify(json, rcond)
    }
    case Filter.ComplexCondition(attr, cond) => verify(json.fieldOrEmptyObject(attr), cond)
    case Filter.GROUP(cond) => verify(json, cond)
    case Filter.NOT(cond) => !verify(json, cond)
    case _ => false
  }

  def extractAttributes(json: Json, attr: String*): Json = {
    Json.obj(attr
      .map{
        case a if a.indexOf(".") < 0 => a -> None
        case a => a.substring(0, a.indexOf(".")) -> Some(a.substring(a.indexOf(".")+1))
      }
      .groupBy(_._1)
      .map(tx => tx._1 -> tx._2.flatMap(_._2))
      .map(t => (t._1, t._2, json.fieldOrNull(t._1)) )
      .map{
        case (a, l, j) if l.isEmpty => a -> j
        case (a, l, j) if j.isObject => a -> extractAttributes(j, l:_*)
        case (a, l, j) if j.isArray => a -> jArray(j.arrayOrEmpty
          .map(ji => extractAttributes(ji, l:_*))
        )
        case (a, _, _) => a -> jNull
      }.toSeq:_*)
  }

  def jsonPath(json: Json, path: String): Option[Json] = {
    val pidx = path.indexOf(".")
    val seg = if (pidx < 0) path -> None else path.substring(0, pidx) -> Some(path.substring(pidx+1))
    val value = json match {
      case j if j.isArray => Some(jArray(j.arrayOrEmpty.flatMap(_.field(seg._1))))
      case j => j.field(seg._1)
    }
    seg._2 match {
      case Some(npath) => value.flatMap(rj => jsonPath(rj, npath))
      case None => value
    }
  }

  def patchJson(json: Json, operator: String, path: Option[String], value: Option[Json]): Either[String, Json] = {
    def interpretPath: (String, Option[JsonField], Option[JsonField], Option[Filter.Condition], Option[Json]) = path match {
      case Some(pth) =>
        val pidx = pth.indexOf(".")
        val segment = if (pidx < 0) pth else pth.substring(0, pidx)
        val npath = if (pidx < 0) None else Some(pth.substring(pidx+1))
        segment match {
          case cmd if cmd.matches("^.+\\[.+\\]$") => Filter.Interpreter.interpretOption(cmd) match {
            case Some(Filter.ComplexCondition(seg, cond)) => (operator, Some(seg), npath, Some(cond), value)
            case _ => (operator, Some(cmd), npath, None, value)
          }
          case seg => (operator, Some(seg), npath, None, value)
        }
      case None => (operator, None, None, None, value)
    }

    interpretPath match {
      case ("add", _, _, _, None) => Left("No given value to add.")
      case ("add", None, _, _, Some(v)) if !v.isObject => Left("Value must be a Json-Object")
      case ("add", None, _, _, Some(v)) => Right(v.objectFieldsOrEmpty.foldLeft(json){ (curr, fld) => curr.->:(fld -> v.fieldOrNull(fld)) })
      case ("add", Some(attr), None, None, ov @ Some(v)) => json.fieldOrNull(attr) match {
        case curr if curr.isObject => patchJson(curr, operator, None, ov).map(res => json.->:(attr -> res))
        case curr if curr.isArray && v.isArray =>
          val narr = v.arrayOrEmpty
          Right(json.->:(attr -> jArray(curr.arrayOrEmpty.filter(itm => !narr.contains(itm)) ++ narr)))
        case curr if curr.isArray => Right(json.->:(attr -> jArray(curr.arrayOrEmpty.filter(_ != v) :+ v)))
        case _ => Right(json.->:(attr -> v))
      }
      case ("add", Some(attr), npath, None, ov) => patchJson(json.fieldOrEmptyObject(attr), operator, npath, ov).map(res => json.->:(attr -> res))
      case ("add", Some(attr), npath, Some(cond), ov) => json.fieldOrNull(attr) match {
        case curr if curr.isObject && verify(curr, cond) => patchJson(curr, operator, npath, ov).map(res => json.->:(attr -> res))
        case curr if curr.isArray => curr.arrayOrEmpty.map{
          case itm if verify(itm, cond) => patchJson(itm, operator, npath, ov)
          case itm => Right(itm)
        }.foldRight(Right(Nil): Either[String, List[Json]]){ (elem, acc) =>
          for {
            t <- acc.right
            h <- elem.right
          } yield h :: t
        }.map(list => json.->:(attr -> jArray(list)))
        case _ => Right(json)
      }

      case ("replace", _, _, _, None) => Left("No given value to replace")
      case ("replace", None, _, _, Some(v)) if !v.isObject => Left("Value must be a Json-Object")
      case ("replace", None, _, _, Some(v)) => Right(v.objectFieldsOrEmpty.filter(json.hasField(_)).foldLeft(json){ (curr, fld) => curr.->:(fld -> v.fieldOrNull(fld)) })
      case ("replace", Some(attr), None, None, ov @ Some(v)) if json.hasField(attr) => json.fieldOrNull(attr) match {
        case curr if curr.isObject => patchJson(curr, operator, None, ov).map(res => json.->:(attr -> res))
        case curr if curr.isArray && v.isArray => Right(json.->:(attr -> v))
        case curr if curr.isArray => Right(json.->:(attr -> jArray(List(v))))
        case _ => Right(json.->:(attr -> v))
      }
      case ("replace", Some(attr), npath, None, ov) if json.hasField(attr) => patchJson(json.fieldOrEmptyObject(attr), operator, npath, ov).map(res => json.->:(attr -> res))
      case ("replace", Some(attr), npath, Some(cond), ov) if json.hasField(attr) => json.fieldOrNull(attr) match {
        case curr if curr.isObject && verify(curr, cond) => patchJson(curr, operator, npath, ov).map(res => json.->:(attr -> res))
        case curr if curr.isArray => curr.arrayOrEmpty.map{
          case itm if verify(itm, cond) => patchJson(itm, operator, npath, ov)
          case itm => Right(itm)
        }.foldRight(Right(Nil): Either[String, List[Json]]){ (elem, acc) =>
          for {
            t <- acc.right
            h <- elem.right
          } yield h :: t
        }.map(list => json.->:(attr -> jArray(list)))
        case _ => Right(json)
      }

      case ("remove", Some(attr), None, None, _) => Right(json.acursor.downField(attr).delete.undo.getOrElse(json))
      case ("remove", Some(attr), npath, None, _) if json.hasField(attr) => json.fieldOrNull(attr) match {
        case curr if curr.isObject => patchJson(json.fieldOrNull(attr), operator, npath, None).map(res => json.->:(attr -> res))
        case curr if curr.isArray => curr.arrayOrEmpty.map(itm => patchJson(itm, operator, npath, None))
          .foldRight(Right(Nil): Either[String, List[Json]]){ (elem, acc) =>
            for {
              t <- acc.right
              h <- elem.right
            } yield h :: t
          }.map(list => json.->:(attr -> jArray(list)))
        case _ => Right(json)
      }
      case ("remove", Some(attr), npath, Some(cond), _) if json.hasField(attr) => json.fieldOrNull(attr) match {
        case curr if curr.isObject && verify(curr, cond) => patchJson(curr, operator, npath, None)
        case curr if curr.isArray => curr.arrayOrEmpty.map{
          case itm if verify(itm, cond) => patchJson(itm, operator, npath, None)
          case itm => Right(itm)
        }.foldRight(Right(Nil): Either[String, List[Json]]){ (elem, acc) =>
          for {
            t <- acc.right
            h <- elem.right
          } yield h :: t
        }.map(list => json.->:(attr -> jArray(list)))
        case _ => Right(json)
      }

      case _ => Right(json)
    }
  }

  def patchJson(json: Json, patch: Patch): Either[String, Json] = patch.operator match {
    case Patch.PatchOperator.REPLACE => patchJson(json, "replace", patch.path, patch.value)
    case Patch.PatchOperator.REMOVE => patchJson(json, "remove", patch.path, patch.value)
    case _ => patchJson(json, "add", patch.path, patch.value)
  }

}
