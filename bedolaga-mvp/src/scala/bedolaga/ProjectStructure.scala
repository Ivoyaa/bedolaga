package bedolaga

import com.typesafe.config.{Config, ConfigFactory}

final case class ProjectStructure(name: String, mainClass: String,
                                  directory: String, fileToCompile: String)

object ProjectStructure {
  def parse(path: String)(config: Config): ProjectStructure = {
    val conf = config.getConfig(path)

    ProjectStructure(
      name = conf.getString("name"),
      mainClass = conf.getString("main-class"),
      directory = conf.getString("directory"),
      fileToCompile = conf.getString("file-to-compile")
    )
  }
}
