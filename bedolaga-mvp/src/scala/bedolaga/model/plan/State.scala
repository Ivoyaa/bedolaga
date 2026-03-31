package bedolaga.model.plan

import bedolaga.model.ProjectStructure

import java.io.File
import java.nio.file.Path

abstract class State(val project: ProjectStructure) {
  val outputTarget = new File(s"${project.directory}/compiled")

  val projectDirectoryFile = new File(project.directory)

  val packageTarget = new File(s"${project.directory}/packaged")

  def fetched: Option[Set[File]]

  def compiled: Option[Set[File]]

  def packaged: Option[Path]
}
