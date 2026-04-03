package bedolaga.model

import bedolaga.model.structure.ProjectName
import com.typesafe.config.Config

import scala.jdk.CollectionConverters.IterableHasAsScala

final case class ProjectStructure(
    name: ProjectName,
    mainClass: String,
    directory: String,
    scalaVersion: String,
    dependencies: List[Dependency],
    prerequisites: List[ProjectName]
)

object ProjectStructure {
  def parse(path: String)(config: Config): ProjectStructure = {
    val conf = config.getConfig(path)

    ProjectStructure(
      name = ProjectName(conf.getString("name")),
      mainClass = conf.getString("main-class"),
      directory = conf.getString("directory"),
      scalaVersion = conf.getString("scala-version"),
      dependencies = conf.getConfigList("dependencies").asScala.toList.map { conf =>
        Dependency(
          org = conf.getString("org"),
          module = conf.getString("module"),
          version = conf.getString("version")
        )
      },
      prerequisites = conf.getStringList("prerequisites").asScala.toList.map { name =>
        ProjectName(name)
      }
    )
  }
}
