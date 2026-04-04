package bedolaga.plan.model

import bedolaga.structure.model.{BuildStructure, ProjectStructure}

import java.io.File
import java.nio.file.Path

abstract class State(val structure: BuildStructure) {
  def fetched: Option[Set[File]]

  def compiled: Option[Set[File]]

  def packaged: Option[Set[Path]]
}
