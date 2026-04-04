package bedolaga.structure.model

import com.typesafe.config.Config

import java.nio.file.Path

final case class ProjectStructure(
    name: ProjectName,
    mainClass: String,
    directory: String,
    scalaVersion: String,
    dependencies: Set[Dependency],
    prerequisites: Set[ProjectName]
) {
  val compilationPath: Path = Path.of(directory, "compiled")
  val packagePath: Path = Path.of(directory, "packaged")
  val fetchPath: Path = Path.of(directory, "fetched")
}