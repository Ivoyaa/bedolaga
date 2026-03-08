package bedolaga

import com.typesafe.config.ConfigFactory
import coursier.core.{Configuration, DependencyManagement, MinimizedExclusions, Publication}
import coursier.{Dependency, Fetch, Module, ModuleName, Organization}

import scala.tools.nsc._
import java.io.{File, FileFilter}
import java.net.URLClassLoader

object Build extends App {
  val project = ProjectStructure.parse("build-structure")(
    ConfigFactory.parseFile(new File("example/setup.conf"))
  )

  val outputTarget = new File(s"${project.directory}/compiled")
  outputTarget.mkdirs()

  val settings = new Settings()
  settings.usejavacp.value = true
  settings.outputDirs.setSingleOutput(outputTarget.getPath)
//  settings.verbose.value = true

//  val global = new Global(settings)
//
//  val run = new global.Run

  val projectDirectoryFile = new File(project.directory)
  val filesToCompile = getFilesRecurrently(projectDirectoryFile)

  val compilerDependency = new Dependency(
    module = new Module(
      Organization("org.scala-lang"),
      ModuleName("scala-compiler"),
      Map.empty
    ),
    version = "2.13.18",
    configuration = Configuration.empty,
    minimizedExclusions = Set.empty[(Organization, ModuleName)],
    publication = Publication.empty,
    optional = false,
    transitive = false
  )

  val libraryDependency = new Dependency(
    module = new Module(
      Organization("org.scala-lang"),
      ModuleName("scala-library"),
      Map.empty
    ),
    version = "2.13.18",
    configuration = Configuration.empty,
    minimizedExclusions = Set.empty[(Organization, ModuleName)],
    publication = Publication.empty,
    optional = false,
    transitive = false
  )

  val reflectDependency = new Dependency(
    module = new Module(
      Organization("org.scala-lang"),
      ModuleName("scala-reflect"),
      Map.empty
    ),
    version = "2.13.18",
    configuration = Configuration.empty,
    minimizedExclusions = Set.empty[(Organization, ModuleName)],
    publication = Publication.empty,
    optional = false,
    transitive = false
  )
  val files = Fetch()
    .addDependencies(compilerDependency, libraryDependency, reflectDependency)
    .run()

  val classLoader = new URLClassLoader(files.map(_.toURI.toURL).toArray)

  println(classLoader.getURLs.mkString("Array(", ", ", ")"))

//  java -classpath ".:/Users/user/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.18/scala-reflect-2.13.18.jar:/Users/user/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.18/scala-library-2.13.18.jar:/Users/user/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.18/scala-compiler-2.13.18.jar" scala.tools.nsc.Main -usejavacp -d example/compiled example/my/shiny/app/HelloWorld.scala example/my/shiny/app/App.scala 

  val global = classLoader
    .loadClass("scala.tools.nsc.Global")
    .getDeclaredConstructor(classOf[Settings])
    .newInstance(settings)
    .asInstanceOf[Global]
  val run = new global.Run

  println(s"COMPILER JARS: ${files.map(_.getPath)}")
  println(s"FILES TO COMPILE: ${filesToCompile.map(_.getPath).mkString("Array(", ", ", ")")}")

  run.compile(filesToCompile.filter(_.getPath.contains(".scala")).map(_.getPath))

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
