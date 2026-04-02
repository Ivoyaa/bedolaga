package my.shiny.app

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

object MyApp extends App {
  val hw = HelloWorld("hello", "world")

  val json = hw.asJson.noSpaces
  println(json)

  println(hw)

//  throw new RuntimeException(s"EMAE FAILED")
}