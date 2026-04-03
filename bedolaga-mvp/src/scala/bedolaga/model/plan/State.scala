package bedolaga.model.plan

import bedolaga.model.ProjectStructure

import java.io.File
import java.nio.file.Path

abstract class State(val project: ProjectStructure) {
  def fetched: Option[Set[File]]

  def compiled: Option[Set[File]]

  def packaged: Option[Set[Path]]
}
