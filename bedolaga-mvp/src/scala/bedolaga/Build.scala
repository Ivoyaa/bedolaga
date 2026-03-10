package bedolaga

import bedolaga.model._
import com.typesafe.config.ConfigFactory
import coursier.Fetch

import scala.sys.process._
import java.io.{File, FileFilter}

object Build extends App {
  val project = ProjectStructure.parse("build-structure")(
    ConfigFactory.parseFile(new File("example/setup.conf"))
  )

  val outputTarget = new File(s"${project.directory}/compiled")
  outputTarget.mkdirs()

  val projectDirectoryFile = new File(project.directory)

  val compilerDependency = Dependency(
    org = "org.scala-lang",
    module = "scala-compiler",
    version = project.scalaVersion
  ).asCoursierDependency

  val libraryDependency = Dependency(
    org = "org.scala-lang",
    module = "scala-library",
    version = project.scalaVersion
  ).asCoursierDependency

  val reflectDependency = Dependency(
    org = "org.scala-lang",
    module = "scala-reflect",
    version = project.scalaVersion
  ).asCoursierDependency

  val compilerJars = Fetch()
    .addDependencies(compilerDependency, libraryDependency, reflectDependency)
    .withClasspathOrder(true)
    .run()

  val filesToCompile =
    getFilesRecurrently(projectDirectoryFile).filter(_.getPath.contains(".scala")).map(_.getPath)

  val command =
    s"""java -classpath
       |".:${compilerJars.mkString(":")}"
       |scala.tools.nsc.Main
       |-usejavacp
       |-d example/compiled
       |${filesToCompile.mkString(" ")}""".stripMargin.replaceAll("\n"," ")

  println(s"COMMAND: $command")
  val _ = command.!!

  def getFilesRecurrently(fileOrDirectory: File): List[File] = {
    if (fileOrDirectory.isFile) List(fileOrDirectory)
    else
      fileOrDirectory
        .listFiles()
        .toList
        .foldLeft(List.empty[File]) { case (acc, fileOrDir) =>
          acc ::: getFilesRecurrently(fileOrDir)
        }
  }

}

/*
Start programmatically.

  import scala.tools.nsc._
  import java.net.URLClassLoader

  val settings = new Settings()
  settings.usejavacp.value = true
  settings.outputDirs.setSingleOutput(outputTarget.getPath)
  settings.verbose.value = true

  val global = new Global(settings)

  val run = new global.Run

  val classLoader = new URLClassLoader(files.map(_.toURI.toURL).toArray) // find jar from artifacts, but we can just point it as direct dependency in bleep
  val global = classLoader
    .loadClass("scala.tools.nsc.Global")
    .getDeclaredConstructor(classOf[Settings])
    .newInstance(settings)
    .asInstanceOf[Global]
  val run = new global.Run


  run.compile(filesToCompile.filter(_.getPath.contains(".scala")).map(_.getPath))
 */
