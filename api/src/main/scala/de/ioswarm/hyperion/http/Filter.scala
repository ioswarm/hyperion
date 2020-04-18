package de.ioswarm.hyperion.http

import java.util.Base64

import de.ioswarm.time.{Date, DateTime, Time}

trait Filter {

  sealed trait Operator
  object Operator {
    case object EQ extends Operator
    case object NE extends Operator
    case object CO extends Operator
    case object SW extends Operator
    case object EW extends Operator
    case object PR extends Operator
    case object GT extends Operator
    case object GE extends Operator
    case object LT extends Operator
    case object LE extends Operator

    def byName(name: String): Option[Operator] = name.toUpperCase match {
      case "EQ" => Some(EQ)
      case "NE" => Some(NE)
      case "CO" => Some(CO)
      case "SW" => Some(SW)
      case "EW" => Some(EW)
      case "PR" => Some(PR)
      case "GT" => Some(GT)
      case "GE" => Some(GE)
      case "LT" => Some(LT)
      case "LE" => Some(LE)
      case _ => None
    }
  }

  sealed trait Logical
  object Logical {
    case object AND extends Logical
    case object OR extends Logical

    def byName(name: String): Option[Logical] = name.toUpperCase match {
      case "AND" => Some(AND)
      case "OR" => Some(OR)
      case _ => None
    }
  }

  object ValueEncode {

    def apply[T](f: T => String): ValueEncode[T] = new ValueEncode[T] {
      def encode(t: T): String = f(t)
    }

  }
  trait ValueEncode[T] {

    def encode(t: T): String

  }

  implicit val _stringValueEncode: ValueEncode[String] = ValueEncode( s => s""""$s"""" )
  implicit val _intValueEncode: ValueEncode[Int] = ValueEncode( i => i.toString )
  implicit val _longValueEncode: ValueEncode[Long] = ValueEncode( l => l.toString )
  implicit val _floatValueEncode: ValueEncode[Float] = ValueEncode( f => f.toString )
  implicit val _doubleValueEncode: ValueEncode[Double] = ValueEncode( d => d.toString )
  implicit val _booleanValueEncode: ValueEncode[Boolean] = ValueEncode( b => b.toString )
  implicit val _byteArrayValueEncode: ValueEncode[Array[Byte]] = ValueEncode( arr => Base64.getEncoder.encodeToString(arr))

  implicit val _dateTimeValueEncode: ValueEncode[DateTime] = ValueEncode( dt => dt.format("\"yyyy-MM-ddTHH:mm:ssZ\"") )
  implicit val _dateValueEncode: ValueEncode[Date] = ValueEncode( d => d.format("\"yyyy-MM-dd\"" ))
  implicit val _timeValueEncode: ValueEncode[Time] = ValueEncode( t => t.format("\"HH:mm:ss\"") )

  case class Conditional(condition: Condition, logical: Logical, attributeName: String) {

    private def operator(op: Operator, value: Option[String]): Condition = CompoundCondition(condition, logical, AttributeCondition(attributeName, op, value))
    def EQ[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.EQ, Some(encode.encode(t)))
    def NE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.NE, Some(encode.encode(t)))
    def CO[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.CO, Some(encode.encode(t)))
    def SW[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.SW, Some(encode.encode(t)))
    def EW[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.EW, Some(encode.encode(t)))
    def PR: Condition = operator(Operator.PR, None)
    def GT[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.GT, Some(encode.encode(t)))
    def GE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.GE, Some(encode.encode(t)))
    def LT[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.LT, Some(encode.encode(t)))
    def LE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.LE, Some(encode.encode(t)))

  }

  trait Condition {
    this: Condition =>

    def AND(attributeName: String): Conditional = Conditional(this, Logical.AND, attributeName)
    def AND(condition: Condition): Condition = CompoundCondition(this, Logical.AND, condition)

    def OR(attributeName: String): Conditional = Conditional(this, Logical.OR, attributeName)
    def OR(condition: Condition): Condition = CompoundCondition(this, Logical.OR, condition)


    def command: String

  }

  case class CompoundCondition(left: Condition, logical: Logical, right: Condition) extends Condition {

    override def command: String = s"${left.command} $logical ${right.command}"

  }

  case class AttributeCondition(attributeName: String, operator: Operator, value: Option[String]) extends Condition {

    override def command: String = s"$attributeName $operator ${value.getOrElse("")}".trim

  }

  case class ComplexCondition(attributeName: String, condition: Condition) extends Condition {

    override def command: String = s"$attributeName[${condition.command}]"

  }

  case class GROUP(condition: Condition) extends Condition {

    override def command: String = s"(${condition.command})"

  }

  case class NOT(condition: Condition) extends Condition {

    override def command: String = s"NOT ${condition.command}"

  }

  object Interpreter {

    private def interpretAttributeCondition(command: String): Either[String, AttributeCondition] = {
      val fs = command.indexOf(" ")
      val ls = command.indexOf(" ", fs + 1)
      (if (ls > fs) (command.substring(0, fs), command.substring(fs + 1, ls).toUpperCase, Some(command.substring(ls + 1))) else (command.substring(0, fs), command.substring(fs + 1).toUpperCase, None)) match {
        case (_, cmd, None) if cmd != "PR" => Left(s"Found '$cmd' but 'PR' expected or missing value.")
        case (attr, _, None) => Right(AttributeCondition(attr, Operator.PR, None))
        case (_, cmd, Some(_)) if cmd == "PR" => Left(s"Operator 'PR' must not have an value.")
        case (attr, cmd, value) => Operator.byName(cmd) match {
          case Some(op) => Right(AttributeCondition(attr, op, value))
          case None => Left(s"Unknown operator '$cmd'.")
        }
      }
    }

    private def interpretFirstLogicalOperator(command: String): (String, Option[Logical], Option[String]) =
      (command.toLowerCase.indexOf(" and "), command.toLowerCase.indexOf(" or ")) match {
        case (ai, oi) if ai < 0 && oi < 0 => (command, None, None)
        case (ai, oi) if ai > 0 && (oi < 0 || ai < oi) => (command.substring(0, ai), Some(Logical.AND), Some(command.substring(ai + 5)))
        case (ai, oi) if oi > 0 && (ai < 0 || oi < ai) => (command.substring(0, oi), Some(Logical.OR), Some(command.substring(oi + 4)))
      }

    private def interpretLogicalOperator(command: String): Either[String, (Logical, Condition)] = {
      val tcommand = command.trim
      tcommand.toLowerCase match {
        case cmd if cmd.startsWith("and ") => interpret(tcommand.substring(4)).map(c => Logical.AND -> c)
        case cmd if cmd.startsWith("or ") => interpret(tcommand.substring(3)).map(c => Logical.OR -> c)
        case _ => Left(s"Malformed filter at >$tcommand")
      }
    }

    private def cutFirstGroupCommand(command: String): Either[String, (String, Option[String])] = {
      val m = command.map {
        case '(' => 1 -> 1
        case ')' => -1 -> 2
        case _ => 0 -> 0
      }

      val idxs = (m.indices zip m)
        .map(t => (t._1, t._2._1, t._2._2))
        .filter(_._2 != 0)
        .scanLeft((-1, 0, 0))((in, c) => (c._1, c._2 + in._2, c._3))
        .drop(1)
        .filter(t => (t._2 == 1 && t._3 == 1) || (t._2 == 0 && t._3 == 2))
        .map(_._1)
        .take(2)

      (idxs.head, idxs.lastOption) match {
        case (_, None) => Left(s"Unclosed bracket >$command")
        case (a, Some(b)) if b + 1 >= command.length => Right((command.substring(a + 1, b), None))
        case (a, Some(b)) => Right((command.substring(a + 1, b), Some(command.substring(b + 1).trim)))
      }
    }

    private def cutFirstComplexCommand(command: String): Either[String, (String, String, Option[String])] = {
      val tcommand = command.trim
      val m = tcommand.map {
        case '[' => 1 -> 1
        case ']' => -1 -> 2
        case _ => 0 -> 0
      }

      val idxs = (m.indices zip m)
        .map(t => (t._1, t._2._1, t._2._2))
        .filter(_._2 != 0)
        .scanLeft((-1, 0, 0))((in, c) => (c._1, c._2 + in._2, c._3))
        .drop(1)
        .filter(t => (t._2 == 1 && t._3 == 1) || (t._2 == 0 && t._3 == 2))
        .map(_._1)
        .take(2)

      (idxs.head, idxs.lastOption) match {
        case (_, None) => Left(s"Unclosed square bracket >$command")
        case (a, Some(b)) if b + 1 >= tcommand.length => Right((tcommand.substring(0, a), tcommand.substring(a + 1, b), None))
        case (a, Some(b)) => Right((tcommand.substring(0, a), tcommand.substring(a + 1, b), Some(tcommand.substring(b + 1).trim)))
      }
    }

    def interpret(command: String): Either[String, Condition] = {
      val tcommand = command.trim

      tcommand.toLowerCase match {
        case cmd if cmd.startsWith("not ") || cmd.startsWith("not(") => interpret(tcommand.substring(3)).map(ac => NOT(ac))
        case cmd if cmd.startsWith("(") => cutFirstGroupCommand(tcommand) match {
          case Left(msg) => Left(msg)
          case Right((inCmd, None)) => interpret(inCmd).map(ac => GROUP(ac))
          case Right((inCmd, Some(outCmd))) => interpretLogicalOperator(outCmd).flatMap(t => interpret(inCmd).map(inC => CompoundCondition(GROUP(inC), t._1, t._2)))
        }
        case cmd if cmd.contains("[") => cutFirstComplexCommand(tcommand) match {
          case Left(msg) => Left(msg)
          case Right((attr, inCmd, None)) => interpret(inCmd).map(ac => ComplexCondition(attr, ac))
          case Right((attr, inCmd, Some(outCmd))) => interpretLogicalOperator(outCmd).flatMap(t => interpret(inCmd).map(inC => CompoundCondition(ComplexCondition(attr, inC), t._1, t._2)))
        }
        case cmd if cmd.contains("and") || cmd.contains("or") => interpretFirstLogicalOperator(tcommand) match {
          case (attrCmd, Some(lop), Some(secCmd)) => interpret(attrCmd).flatMap(ac => interpret(secCmd).map(sc => CompoundCondition(ac, lop, sc)))
          case _ => interpretAttributeCondition(tcommand) //Left(s"Malformed filter '$command'")
        }
        case _ => interpretAttributeCondition(tcommand)
      }
    }

    def interpretOption(command: String): Option[Condition] = interpret(command).toOption

  }

  implicit class _filterStringExtender(val s: String) {

    private def operator(op: Operator, value: Option[String]): Condition = AttributeCondition(s, op, value)
    def EQ[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.EQ, Some(encode.encode(t)))
    def NE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.NE, Some(encode.encode(t)))
    def CO[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.CO, Some(encode.encode(t)))
    def SW[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.SW, Some(encode.encode(t)))
    def EW[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.EW, Some(encode.encode(t)))
    def PR: Condition = operator(Operator.PR, None)
    def GT[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.GT, Some(encode.encode(t)))
    def GE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.GE, Some(encode.encode(t)))
    def LT[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.LT, Some(encode.encode(t)))
    def LE[T](t: T)(implicit encode: ValueEncode[T]): Condition = operator(Operator.LE, Some(encode.encode(t)))

    def COMPLEX(condition: Condition): Condition = ComplexCondition(s, condition)

  }

}
object Filter extends Filter
