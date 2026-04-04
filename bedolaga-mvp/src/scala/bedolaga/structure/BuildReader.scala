package bedolaga.structure

import bedolaga.structure.model.{BuildStructure, Dependency, ProjectName, ProjectStructure}
import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.nio.file.Path
import scala.jdk.CollectionConverters._

object BuildReader {
  def read(setup: File): BuildStructure = {
    val config = ConfigFactory.parseFile(setup)

    BuildStructure(
      config.getObject("build-structure").asScala.toMap.map { case (name, _) =>
        val projectConfig = config.getConfig("build-structure").getConfig(name)

        ProjectName(name) -> ProjectStructure(
          name = ProjectName(name),
          mainClass = projectConfig.getString("main-class"),
          directory = projectConfig.getString("directory"),
          scalaVersion = projectConfig.getString("scala-version"),
          dependencies =
            projectConfig.getConfigList("dependencies").asScala.toSet.map[Dependency] { dependencyConfig =>
              Dependency(
                org = dependencyConfig.getString("org"),
                module = dependencyConfig.getString("module"),
                version = dependencyConfig.getString("version")
              )
            },
          prerequisites = projectConfig.getStringList("prerequisites").asScala.toSet.map { name =>
            ProjectName(name)
          }
        )
      }
    )

  }
}
