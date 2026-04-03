package my.shiny.corelib

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AddResult(x: Int, y: Int, result: Int)

object AddResult {
  implicit val jsonDecoder: JsonDecoder[AddResult] = DeriveJsonDecoder.gen[AddResult]
  implicit val jsonEncoder: JsonEncoder[AddResult] = DeriveJsonEncoder.gen[AddResult]
}