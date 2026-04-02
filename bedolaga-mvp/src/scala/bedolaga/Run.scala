package bedolaga

import bedolaga.model.plan._
import bedolaga.model.{Build, Dependency, ProjectStructure}
import com.typesafe.config.ConfigFactory
import coursier.{Fetch => CsFetch}

import scala.sys.process._
import java.io.{ByteArrayOutputStream, File}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.jar.{Attributes, JarEntry, JarOutputStream}
import scala.annotation.tailrec
import scala.language.postfixOps

object Run extends App {
  interpret {
    val pr = ProjectStructure.parse("build-structure")(
      ConfigFactory.parseFile(new File("example/setup.conf"))
    )

    Build(
      state = new State(pr) {
        override def fetched: Option[Set[File]] = None

        override def compiled: Option[Set[File]] = None

        override def packaged: Option[Path] = None
      },
      executionPlan = Fetch(
        dependencies = pr.dependencies,
        fetchPath = Path.of(pr.directory, "fetched")
      ) ::
        ScalaCompile(
          compilationTarget = Path.of(pr.directory),
          compilationPath = Path.of(pr.directory, "compiled"),
          fetchPath = Path.of(pr.directory, "fetched")
        ) ::
        Package(
          artifactsCompiled = List(Path.of(pr.directory, "compiled")),
          packagePath = Path.of(pr.directory, "packaged")
        ) ::
        RunApp(
          artifactsFetched = List(Path.of(pr.directory, "fetched")),
          artifactsPackaged = Path.of(pr.directory, "packaged"),
          mainClass = pr.mainClass
        ) :: Nil
    )
  }

  @tailrec
  def interpret(build: Build): Build = build.executionPlan match {
    case Nil =>
      println(s"EXECUTION FINISHED")
      build

    case Fetch(dependencies, fetchPath) :: tail =>
      val deps = dependencies
      val fetchedFiles = CsFetch()
        .addDependencies(deps.map(_.asCoursierDependency): _*)
        .withClasspathOrder(true)
        .run()

      val _ = fetchPath.toFile.mkdirs()

      val moved =
        fetchedFiles.map(f => Files.move(f.toPath, fetchPath.resolve(f.getName), REPLACE_EXISTING))

      println(s"DEPENDENCIES: $moved")

      interpret(
        Build(
          state = new State(build.state.project) {
            override def fetched: Option[Set[File]] =
              Some(fetchedFiles.toSet ++ build.state.fetched.getOrElse(Set.empty))

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Path] = build.state.packaged
          },
          executionPlan = tail
        )
      )

    case ScalaCompile(compilationTarget, compilationPath, fetchPath) :: tail =>
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

      val filesToCompile = getFilesRecurrently(compilationTarget.toFile)
        .filter(_.getPath.contains(".scala"))
        .map(_.getPath)

      val fetchedFiles =
        getFilesRecurrently(fetchPath.toFile).filter(_.getPath.contains(".jar"))

      val command =
        s"""java -classpath
           |".:${compilerJars
          .mkString(":")}:${fetchedFiles.mkString(":")}"
           |scala.tools.nsc.Main
           |-usejavacp
           |-d example/compiled
           |${filesToCompile.mkString(" ")}""".stripMargin.replaceAll("\n", " ")

      val _ = compilationPath.toFile.mkdirs()

      println(s"COMMAND: $command")
      val _ = command.!!

      val compiledFiles =
        getFilesRecurrently(compilationPath.toFile).filter(_.getPath.contains(".class"))

      interpret(
        Build(
          state = new State(build.state.project) {
            override def fetched: Option[Set[File]] =
              Some(build.state.fetched.getOrElse(Set.empty))

            override def compiled: Option[Set[File]] =
              Some(build.state.compiled.getOrElse(Set.empty) ++ compiledFiles)

            override def packaged: Option[Path] = build.state.packaged
          },
          executionPlan = tail
        )
      )

    case Package(artifactsCompiled, packagePath) :: tail =>
      val filesToPackage = artifactsCompiled.flatMap(p =>
        getFilesRecurrently(p.toFile)
          .filter(_.getPath.contains(".class"))
          .map(f => (f, f.getPath.replaceAll(s"${p.toFile.getPath}/", "")))
      )

      println(s"PACKAGE: ${filesToPackage}")

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
      val jar = filesToPackage.foldLeft(streamJar) { case (jar, (file, shortPath)) =>
        val entry = new JarEntry(shortPath)
        jar.putNextEntry(entry)
        jar.write(Files.readAllBytes(file.toPath))
        jar.closeEntry()
        jar
      }

      jar.close()

      val jarBytes = stream.toByteArray

      val _ = packagePath.toFile.mkdirs()

      val _ = Files.write(
        new File(
          s"${packagePath}/${build.state.project.name}.jar"
        ).toPath,
        jarBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )

      interpret(
        Build(
          state = new State(build.state.project) {
            override def fetched: Option[Set[File]] = build.state.fetched

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Path] = if (build.state.packaged.nonEmpty)
              throw new RuntimeException("Non empty packaged for some reason!")
            else Some(packagePath)
          },
          executionPlan = tail
        )
      )

    case RunApp(fetchPaths, artifactsPackaged, mainClass) :: tail =>
      val packagedApp = getFilesRecurrently(artifactsPackaged.toFile)
        .filter(_.getPath.contains(".jar"))
        .head

      val artifactsFetched = fetchPaths.flatMap(p =>
        getFilesRecurrently(p.toFile)
          .filter(_.getPath.contains(".jar"))
      )

      val runCommand =
        s"""java -classpath
           |".:${artifactsFetched
          .mkString(":")}:${packagedApp.getPath}"
           |${mainClass}""".stripMargin.replaceAll("\n", " ")

      println(s"RUN COMMAND: $runCommand")

      val result = runCommand.!!

      println(Console.GREEN + result + Console.RESET)

      interpret(
        Build(
          state = new State(build.state.project) {
            override def fetched: Option[Set[File]] = build.state.fetched

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Path] = build.state.packaged
          },
          executionPlan = tail
        )
      )
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
