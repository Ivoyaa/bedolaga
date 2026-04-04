package my.shiny.app

import my.shiny.corelib._
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir._
import zio._
import zio.http.{Response => ZioHttpResponse, Routes, Server}

object EndpointDefinition {
  // a simple string-only endpoint
  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get
      .in("hello")
      .in(path[String]("name"))
      .out(stringBody)

  // an endpoint which responds which json, using zio-json
  val add: PublicEndpoint[(Int, Int), Unit, AddResult, Any] =
    endpoint.get
      .in("add")
      .in(path[Int]("x"))
      .in(path[Int]("y"))
      .out(jsonBody[AddResult])
}