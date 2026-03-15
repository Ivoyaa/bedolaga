package bedolaga

import bedolaga.model._
import com.typesafe.config.ConfigFactory
import coursier.{Fetch, Resolve}

import scala.sys.process._
import java.io.{ByteArrayOutputStream, File, FileFilter}
import java.nio.file.{Files, StandardOpenOption}
import java.util.jar.{Attributes, JarEntry, JarOutputStream}

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

  val dependencies = Fetch()
    .addDependencies(project.dependencies.map(_.asCoursierDependency): _*)
    .withClasspathOrder(true)
    .run()

  println(s"DEPENDENCIES: $dependencies")

  val command =
    s"""java -classpath
       |".:${compilerJars.mkString(":")}:${dependencies.mkString(":")}"
       |scala.tools.nsc.Main
       |-usejavacp
       |-d example/compiled
       |${filesToCompile.mkString(" ")}""".stripMargin.replaceAll("\n", " ")

  println(s"COMMAND: $command")
  val _ = command.!!

  val filesToPackage =
    getFilesRecurrently(outputTarget).filter(_.getPath.contains(".class"))

  println(s"PACKAGE: ${filesToPackage.map(_.getPath)}")

  val manifest = {
    val mfst = new java.util.jar.Manifest()
    val attrs = mfst.getMainAttributes
    attrs.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0")
    attrs.put(Attributes.Name.IMPLEMENTATION_TITLE, project.name)
    attrs.put(Attributes.Name.MAIN_CLASS, project.mainClass)
    mfst
  }

  val stream = new ByteArrayOutputStream(1024 * 1024)
  val streamJar = new JarOutputStream(stream, manifest)

  val jar = filesToPackage.foldLeft(streamJar) { case (jar, file) =>
    val entry = new JarEntry(file.getPath)
    jar.putNextEntry(entry)
    jar.write(Files.readAllBytes(file.toPath))
    jar.closeEntry()
    jar
  }

  jar.close()

  val jarBytes = stream.toByteArray

  val packageTarget = new File(s"${project.directory}/packaged")

  packageTarget.mkdirs()

  Files.write(
    new File(s"${packageTarget.getPath}/${project.name}.jar").toPath,
    jarBytes,
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING
  )

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
