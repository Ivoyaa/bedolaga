package bedolaga

import com.typesafe.config.ConfigFactory
import scala.tools.nsc._

import java.io.File

object Build extends App {
  val project = ProjectStructure.parse("build-structure")(
    ConfigFactory.parseFile(new File("example/setup.conf"))
  )

  val outputTarget = new File(s"${project.directory}/compiled")
  outputTarget.mkdirs()

  val settings = new Settings()
  settings.usejavacp.value = true
  settings.outputDirs.setSingleOutput(outputTarget.getPath)
  settings.verbose.value = true

  val global = new Global(settings)

  val run = new global.Run

  run.compile(List(project.fileToCompile))

}
