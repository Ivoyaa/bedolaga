package bedolaga.plan.model

import bedolaga.structure.model.{Dependency, ProjectName}

import java.nio.file.Path

sealed trait Phase

object Phase {
  case class Fetch(dependencies: Set[Dependency], fetchPath: Path) extends Phase

  case class ScalaCompile(compilationTarget: Path, compilationPath: Path, fetchPaths: Set[Path])
    extends Phase

  case class Package(artifactsCompiled: Set[Path], packagePath: Path, projectName: ProjectName) extends Phase

  case class RunApp(artifactsFetched: Set[Path], artifactsPackaged: Path, mainClass: String)
    extends Phase
}
