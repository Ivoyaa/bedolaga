package bedolaga.model.plan

import bedolaga.model.Dependency
import bedolaga.model.structure.ProjectName

import java.nio.file.Path

sealed trait Step

case class Fetch(dependencies: List[Dependency], fetchPath: Path) extends Step

case class ScalaCompile(compilationTarget: Path, compilationPath: Path, fetchPaths: List[Path])
  extends Step

case class Package(artifactsCompiled: List[Path], packagePath: Path, projectName: ProjectName) extends Step

case class RunApp(artifactsFetched: List[Path], artifactsPackaged: Path, mainClass: String)
  extends Step
