package bedolaga.model

import com.typesafe.config.Config

import scala.jdk.CollectionConverters.IterableHasAsScala

final case class ProjectStructure(
    name: String,
    mainClass: String,
    directory: String,
    scalaVersion: String,
    dependencies: List[Dependency]
)

object ProjectStructure {
  def parse(path: String)(config: Config): ProjectStructure = {
    val conf = config.getConfig(path)

    ProjectStructure(
      name = conf.getString("name"),
      mainClass = conf.getString("main-class"),
      directory = conf.getString("directory"),
      scalaVersion = conf.getString("scala-version"),
      dependencies = conf.getConfigList("dependencies").asScala.toList.map { conf =>
        Dependency(
          org = conf.getString("org"),
          module = conf.getString("module"),
          version = conf.getString("version")
        )
      }
    )
  }
}
