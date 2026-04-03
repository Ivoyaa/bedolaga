package my.shiny.app

import my.shiny.corelib._
import my.shiny.app._
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir._
import zio._
import zio.http.{Response => ZioHttpResponse, Routes, Server}

object HelloWorldZioHttpServer extends ZIOAppDefault {
    import EndpointDefinition._

    val app: Routes[Any, ZioHttpResponse] =
      ZioHttpInterpreter().toHttp(
        helloWorld.zServerLogic(name =>
          ZIO.succeed(println(s"Request with name: $name")) *> ZIO.succeed(s"Hello, $name!")
        )
      ) ++
        ZioHttpInterpreter().toHttp(add.zServerLogic { case (x, y) =>
          ZIO.succeed(println(s"Request with x and y: $x $y")) *> ZIO.succeed(AddResult(x, y, x + y))
        })

  override def run =
    Server
      .serve(app)
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
      .exitCode
}
