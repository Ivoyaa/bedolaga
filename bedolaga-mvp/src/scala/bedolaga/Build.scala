package bedolaga

import com.typesafe.config.ConfigFactory
import fansi._
import scala.tools.nsc._

import java.io.File

object Build extends App {
  val project = ProjectStructure.parse("build-structure")(
    ConfigFactory.parseFile(new File("example/setup.conf"))
  )

  val settings = new Settings()
  settings.usejavacp.value = true
  settings.outputDirs.setSingleOutput(project.directory)
  val global = new Global(settings)

  val run = new global.Run

  run.compile(List(project.fileToCompile))

}
