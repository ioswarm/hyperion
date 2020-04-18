package de.ioswarm.hyperion.cli.mongo

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters

object Filter {

  import de.ioswarm.hyperion.http.Filter._

  implicit class _mongoConditionExtender(val condition: Condition) {

    private def _regexEscape(value: String): String = {
      val replacements = List(
        "\\" -> "\\\\"
        , "^"  -> "\\^"
        , "$"  -> "\\$"
        , "*"  -> "\\*"
        , "+"  -> "\\+"
        , "?"  -> "\\?"
        , "."  -> "\\."
        , "("  -> "\\("
        , ")"  -> "\\)"
        , "|"  -> "\\|"
        , "{"  -> "\\{"
        , "}"  -> "\\}"
        , "["  -> "\\["
        , "]"  -> "\\]"
      )
      replacements.foldLeft(value)((v, t) => v.replace(t._1, t._2))
    }

    private def _parseValue(value: String) = value match {
      case v if v.startsWith("\"") || v.endsWith("\"") => v.replace("\"", "")
      case v if v.toLowerCase == "true" || v.toLowerCase == "false" => v.toBoolean
      case v if v.contains(".") => v.toDouble // TODO useRegex
      case v => v.toInt // TODO useRegex ... check if Int
    }

    private def _parseAsBson(prevAttr: Option[String] = None): Either[String, Bson] = condition match {
      case AttributeCondition(attr, op, value) => op match {
        case Operator.EQ => value match {
          case Some(v) => Right(Filters.eq(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.NE => value match {
          case Some(v) => Right(Filters.ne(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.CO => value match {
          case Some(v) => Right(Filters.regex(attr, "^.*"+_parseValue(_regexEscape(v))+".*$"))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.SW => value match {
          case Some(v) => Right(Filters.regex(attr, "^"+_parseValue(_regexEscape(v))+".*$"))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.EW => value match {
          case Some(v) => Right(Filters.regex(attr, "^.*"+_parseValue(_regexEscape(v))+"$"))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.PR => value match {
          case Some(v) => Left(s"Value must not be present for filter '${condition.toString}'")
          case None => Right(Filters.exists(attr))
        }
        case Operator.GT => value match {
          case Some(v) => Right(Filters.gt(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.GE => value match {
          case Some(v) => Right(Filters.gte(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.LT => value match {
          case Some(v) => Right(Filters.lt(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case Operator.LE => value match {
          case Some(v) => Right(Filters.lte(attr, _parseValue(v)))
          case None => Left(s"Value must be present for filter '${condition.toString}'")
        }
        case _ => Left(s"Unknown operator-type '${op.getClass.getName}'")
      }
      case CompoundCondition(lcond, lo, rcond) => lo match {
        case Logical.AND => lcond.parseAsBson.flatMap(lc => rcond.parseAsBson.map(rc => Filters.and(lc, rc)))
        case Logical.OR => lcond.parseAsBson.flatMap(lc => rcond.parseAsBson.map(rc => Filters.or(lc, rc)))
      }
      case ComplexCondition(attr, cond) => Left("Not implemented yet!") // TODO implement complex conditions
      case GROUP(cond) => cond.parseAsBson
      case NOT(cond) => cond.parseAsBson.map(c => Filters.not(c))
      case _ => Left(s"Unsupported condition-type '${condition.getClass.getName}'")
    }

    def parseAsBson: Either[String, Bson] = _parseAsBson()

    def parseAsBsonOption: Option[Bson] = parseAsBson.toOption

  }

}
