package de.ioswarm.hyperion.cli.mongo

import org.mongodb.scala.model.Filters

object FilterTest extends App {

  import de.ioswarm.hyperion.http.Filter._
  import Filter._

  val cond = "name" EQ "\"Hannah\"" AND GROUP("age" GT 17 OR "age" LE 16)
  println(cond)
  println( Filters.and( Filters.eq("name", "Hannah"), Filters.or( Filters.gt("age", 17), Filters.lte("age", 16) ) ) )
  println()
  println()
  println("Test: ")
  println( ("name" CO """"drea"""").parseAsBson )


}
