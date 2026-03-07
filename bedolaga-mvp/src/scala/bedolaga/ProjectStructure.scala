package bedolaga

import com.typesafe.config.{ConfigFactory, Config}

final case class ProjectStructure(name: String, mainClass: String)

object ProjectStructure {
  def parse(path: String)(config: Config): ProjectStructure = {
    val conf = config.getConfig(path)

    ProjectStructure(name = conf.getString("name"), mainClass = conf.getString("main-class"))
  }
}
