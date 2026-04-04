package bedolaga

import bedolaga.plan.PlanBuilder
import bedolaga.plan.model._
import bedolaga.structure._
import bedolaga.structure.model.{Dependency, ProjectName}
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
    val buildStructure = BuildReader.read(new File("example/setup.conf"))

    new PlanBuilder(buildStructure).build(Step.Compile(ProjectName("example-app")), ProjectName("example-app"))

    val prCore = buildStructure.projects(ProjectName("example-core"))
    val prApp = buildStructure.projects(ProjectName("example-app"))

    Build(
      state = new State(buildStructure) {
        override def fetched: Option[Set[File]] = None

        override def compiled: Option[Set[File]] = None

        override def packaged: Option[Set[Path]] = None
      },
      executionPlan = Phase.Fetch(
        dependencies = prCore.dependencies,
        fetchPath = prCore.fetchPath
      ) ::
        Phase.ScalaCompile(
          compilationTarget = Path.of(prCore.directory),
          compilationPath = prCore.compilationPath,
          fetchPaths = Set(prCore.fetchPath)
        ) :: Phase.Package(
          artifactsCompiled = Set(prCore.compilationPath),
          packagePath = prCore.packagePath,
          projectName = prCore.name
        ) ::
        Phase.Fetch(
          dependencies = prApp.dependencies,
          fetchPath = prApp.fetchPath
        ) ::
        Phase.ScalaCompile(
          compilationTarget = Path.of(prApp.directory),
          compilationPath = prApp.compilationPath,
          fetchPaths = Set(
            prApp.fetchPath,
            prCore.fetchPath,
            prCore.packagePath
          )
        ) ::
        Phase.Package(
          artifactsCompiled = Set(prApp.compilationPath),
          packagePath = prApp.packagePath,
          projectName = prApp.name
        ) ::
        Phase.RunApp(
          artifactsFetched = Set(
            prApp.fetchPath,
            prCore.fetchPath,
            prCore.packagePath
          ),
          artifactsPackaged = prApp.packagePath,
          mainClass = prApp.mainClass
        ) :: Nil
    )
  }

  @tailrec
  def interpret(build: Build): Build = build.executionPlan match {
    case Nil =>
      println(s"EXECUTION FINISHED")
      build

    case Phase.Fetch(dependencies, fetchPath) :: tail =>
      val deps = dependencies.toList
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
          state = new State(build.state.structure) {
            override def fetched: Option[Set[File]] =
              Some(fetchedFiles.toSet ++ build.state.fetched.getOrElse(Set.empty))

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Set[Path]] = build.state.packaged
          },
          executionPlan = tail
        )
      )

    case Phase.ScalaCompile(compilationTarget, compilationPath, fetchPaths) :: tail =>
      val compilerDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-compiler",
        version = build.state.structure.projects(ProjectName("example-app")).scalaVersion
      ).asCoursierDependency

      val libraryDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-library",
        version = build.state.structure.projects(ProjectName("example-app")).scalaVersion
      ).asCoursierDependency

      val reflectDependency = Dependency(
        org = "org.scala-lang",
        module = "scala-reflect",
        version = build.state.structure.projects(ProjectName("example-app")).scalaVersion
      ).asCoursierDependency

      // TODO: вынести в кэш компиляторов
      val compilerJars = CsFetch()
        .addDependencies(compilerDependency, libraryDependency, reflectDependency)
        .withClasspathOrder(true)
        .run()

      val filesToCompile = getFilesRecurrently(compilationTarget.toFile)
        .filter(_.getPath.contains(".scala"))
        .map(_.getPath)

      println(s"FETCH PATHS: $fetchPaths")
      val fetchedFiles =
        fetchPaths.flatMap(p => getFilesRecurrently(p.toFile).filter(_.getPath.contains(".jar")))

      val command =
        s"""java -classpath
           |".:${compilerJars
          .mkString(":")}:${fetchedFiles.mkString(":")}"
           |scala.tools.nsc.Main
           |-usejavacp
           |-d $compilationPath
           |${filesToCompile.mkString(" ")}""".stripMargin.replaceAll("\n", " ")

      val _ = compilationPath.toFile.mkdirs()

      println(s"COMMAND: $command")

      val _ = command.!!

      val compiledFiles =
        getFilesRecurrently(compilationPath.toFile).filter(_.getPath.contains(".class"))

      interpret(
        Build(
          state = new State(build.state.structure) {
            override def fetched: Option[Set[File]] =
              Some(build.state.fetched.getOrElse(Set.empty))

            override def compiled: Option[Set[File]] =
              Some(build.state.compiled.getOrElse(Set.empty) ++ compiledFiles)

            override def packaged: Option[Set[Path]] = build.state.packaged
          },
          executionPlan = tail
        )
      )

    case Phase.Package(artifactsCompiled, packagePath, projectName) :: tail =>
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
        attrs.put(Attributes.Name.IMPLEMENTATION_TITLE, projectName.name)
        attrs.put(Attributes.Name.MAIN_CLASS, projectName.name)
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
          s"${packagePath}/${projectName.name}.jar"
        ).toPath,
        jarBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )

      interpret(
        Build(
          state = new State(build.state.structure) {
            override def fetched: Option[Set[File]] = build.state.fetched

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Set[Path]] =
              Some(build.state.packaged.getOrElse(Set.empty) ++ Set(packagePath))
          },
          executionPlan = tail
        )
      )

    case Phase.RunApp(fetchPaths, artifactsPackaged, mainClass) :: tail =>
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

      val logFile =
        new File(build.state.structure.projects(ProjectName("example-app")).directory, "applog.txt")
      val logger = new FileProcessLogger(logFile)

      val result = runCommand.!(logger)

      logger.flush()
      logger.close()

      println(Console.GREEN + result + Console.RESET)

      interpret(
        Build(
          state = new State(build.state.structure) {
            override def fetched: Option[Set[File]] = build.state.fetched

            override def compiled: Option[Set[File]] = build.state.compiled

            override def packaged: Option[Set[Path]] = build.state.packaged
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
