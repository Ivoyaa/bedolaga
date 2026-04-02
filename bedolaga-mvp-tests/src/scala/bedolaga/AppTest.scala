package bedolaga

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Path

class AppTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    println(Path.of("example/").toAbsolutePath)
  }
}
