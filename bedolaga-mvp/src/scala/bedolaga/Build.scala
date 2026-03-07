package bedolaga

import com.typesafe.config.ConfigFactory
import fansi._

import java.io.File

object Build extends App {


  println(
    ProjectStructure.parse("build-structure")(ConfigFactory.parseFile(new File("example/setup.conf")))
  )
}
