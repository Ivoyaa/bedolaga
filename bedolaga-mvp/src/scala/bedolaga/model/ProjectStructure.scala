package bedolaga.model

import com.typesafe.config.Config

final case class ProjectStructure(
    name: String,
    mainClass: String,
    directory: String,
    scalaVersion: String
)

object ProjectStructure {
  def parse(path: String)(config: Config): ProjectStructure = {
    val conf = config.getConfig(path)

    ProjectStructure(
      name = conf.getString("name"),
      mainClass = conf.getString("main-class"),
      directory = conf.getString("directory"),
      scalaVersion = conf.getString("scala-version")
    )
  }
}
