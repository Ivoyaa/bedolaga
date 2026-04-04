package bedolaga.plan

import bedolaga.plan.model.{Build, Step}
import bedolaga.structure.model.{BuildStructure, ProjectName}

import scala.annotation.tailrec

class PlanBuilder(structure: BuildStructure) {
  def build(command: Step, project: ProjectName): Build = {
    val preprojects = structure.projects(project).prerequisites.toList

    val plan: List[Step] = (preprojects :+ project).foldLeft(List.empty[Step]) {
      case (acc, current) =>
        val newStep = command match {
          case Step.Fetch(_)   => Step.Fetch(current)
          case Step.Compile(_) => Step.Compile(current)
          case Step.Package(_) => Step.Package(current)
          case Step.RunApp(_)  => Step.RunApp(current)
        }
        (acc ::: makeChain(newStep)).filter {
          case Step.RunApp(p: ProjectName) if p != project => false // RunApp should not be run on prerequisit-project
          case _ => true
        }
    }

    println(s"PLAN: ${plan.mkString(" -> ")}")

    Build(???, ???)
  }

  private def makeChain(step: Step) = {

    @tailrec
    def recRun(current: Step, acc: List[Step]): List[Step] = current.preStep match {
      case Some(s) => recRun(s.asInstanceOf[Step], s :: acc)
      case None    => acc
    }

    recRun(step, List(step))
  }
}
