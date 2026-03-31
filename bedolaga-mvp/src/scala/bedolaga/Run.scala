package bedolaga

import bedolaga.model.plan._
import bedolaga.model.{Build, Dependency, ProjectStructure}
import com.typesafe.config.ConfigFactory
import coursier.{Fetch => CsFetch}

import scala.sys.process._
import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.jar.{Attributes, JarEntry, JarOutputStream}
import scala.language.postfixOps

object Run extends App {
  val pr = ProjectStructure.parse("build-structure")(
    ConfigFactory.parseFile(new File("example/setup.conf"))
  )

  interpret(
    Build(
      state = new State(pr) {
        override def fetched: Option[Set[File]] = None

        override def compiled: Option[Set[File]] = None

        override def packaged: Option[Path] = None
      },
      executionPlan = Fetch :: ScalaCompile :: Package :: RunApp :: End :: Nil
    )
  )

  def interpret(build: Build): Build = build.executionPlan.head match {
    case End =>
      println(s"Bedolaga finished!")
      Build(state = build.state, executionPlan = List.empty)
    case Fetch =>
      val deps = build.state.project.dependencies
      val fetchedFiles = CsFetch()
        .addDependencies(deps.map(_.asCoursierDependency): _*)
        .withClasspathOrder(true)
        .run()

      println(s"DEPENDENCIES: $deps")

      interpret(Build(
        state = new State(build.state.project) {
          override def fetched: Option[Set[File]] =
            Some(fetchedFiles.toSet ++ build.state.fetched.getOrElse(Set.empty))

          override def compiled: Option[Set[File]] = build.state.compiled

          override def packaged: Option[Path] = build.state.packaged
        },
        executionPlan = build.executionPlan.tail
      ))

    case ScalaCompile =>
      val compilerDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-compiler",
        version = build.state.project.scalaVersion
      ).asCoursierDependency

      val libraryDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-library",
        version = build.state.project.scalaVersion
      ).asCoursierDependency

      val reflectDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-reflect",
        version = build.state.project.scalaVersion
      ).asCoursierDependency

      // TODO: вынести в кэш компиляторов
      val compilerJars = CsFetch()
        .addDependencies(compilerDependency, libraryDependency, reflectDependency)
        .withClasspathOrder(true)
        .run()

      val filesToCompile = getFilesRecurrently(build.state.projectDirectoryFile)
        .filter(_.getPath.contains(".scala"))
        .map(_.getPath)

      val command =
        s"""java -classpath
           |".:${compilerJars
          .mkString(":")}:${build.state.fetched.getOrElse(Set.empty).mkString(":")}"
           |scala.tools.nsc.Main
           |-usejavacp
           |-d example/compiled
           |${filesToCompile.mkString(" ")}""".stripMargin.replaceAll("\n", " ")

      build.state.outputTarget.mkdirs()

      println(s"COMMAND: $command")
      val _ = command.!!

      val compiledFiles =
        getFilesRecurrently(build.state.outputTarget).filter(_.getPath.contains(".class"))

      interpret(Build(
        state = new State(build.state.project) {
          override def fetched: Option[Set[File]] =
            Some(build.state.fetched.getOrElse(Set.empty))

          override def compiled: Option[Set[File]] =
            Some(build.state.compiled.getOrElse(Set.empty) ++ compiledFiles)

          override def packaged: Option[Path] = build.state.packaged
        },
        executionPlan = build.executionPlan.tail
      ))

    case Package =>
      val filesToPackage =
        getFilesRecurrently(build.state.outputTarget).filter(_.getPath.contains(".class"))

      println(s"PACKAGE: ${filesToPackage.map(_.getPath)}")

      val manifest = {
        val mfst = new java.util.jar.Manifest()
        val attrs = mfst.getMainAttributes
        attrs.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0")
        attrs.put(Attributes.Name.IMPLEMENTATION_TITLE, build.state.project.name)
        attrs.put(Attributes.Name.MAIN_CLASS, build.state.project.mainClass)
        mfst
      }

      val stream = new ByteArrayOutputStream(1024 * 1024)
      val streamJar = new JarOutputStream(stream, manifest)
      val jar = filesToPackage.foldLeft(streamJar) { case (jar, file) =>
        val shortPath = file.getPath.replaceAll(s"${build.state.outputTarget.getPath}/", "")
        val entry = new JarEntry(shortPath)
        jar.putNextEntry(entry)
        jar.write(Files.readAllBytes(file.toPath))
        jar.closeEntry()
        jar
      }

      jar.close()

      val jarBytes = stream.toByteArray

      build.state.packageTarget.mkdirs()

      val packagePath = Files.write(
        new File(
          s"${build.state.packageTarget.getPath}/${build.state.project.name}.jar"
        ).toPath,
        jarBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )

      interpret(Build(
        state = new State(build.state.project) {
          override def fetched: Option[Set[File]] = build.state.fetched

          override def compiled: Option[Set[File]] = build.state.compiled

          override def packaged: Option[Path] = if (build.state.packaged.nonEmpty)
            throw new RuntimeException("Non empty packaged for some reason!")
          else Some(packagePath)
        },
        executionPlan = build.executionPlan.tail
      ))

    case RunApp =>
      val runCommand =
        s"""java -classpath
           |".:${build.state.fetched
          .getOrElse(Set.empty)
          .mkString(":")}:${build.state.packaged.get.toAbsolutePath}"
           |${build.state.project.mainClass}""".stripMargin.replaceAll("\n", " ")

      println(s"RUN COMMAND: $runCommand")

      val result = runCommand.!!

      println(result)

      interpret(Build(
        state = new State(build.state.project) {
          override def fetched: Option[Set[File]] = build.state.fetched

          override def compiled: Option[Set[File]] = build.state.compiled

          override def packaged: Option[Path] = build.state.packaged
        },
        executionPlan = build.executionPlan.tail
      ))
  }

  private def getFilesRecurrently(fileOrDirectory: File): List[File] = {
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
