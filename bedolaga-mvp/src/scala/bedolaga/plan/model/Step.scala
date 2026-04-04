package bedolaga.plan.model

import bedolaga.structure.model.{Dependency, ProjectName}

import java.nio.file.Path

sealed trait Step {
  val project: ProjectName

  val preStep: Option[Step]
}

object Step {
  case class Fetch(project: ProjectName) extends Step {
    override val preStep: Option[Step] = None
  }

  case class Compile(project: ProjectName) extends Step {
    override val preStep: Option[Step] = Some(Fetch(project))
  }

  case class Package(project: ProjectName) extends Step {
    override val preStep: Option[Step] = Some(Compile(project))
  }

  case class RunApp(project: ProjectName) extends Step {
    override val preStep: Option[Step] = Some(Package(project))
  }
}