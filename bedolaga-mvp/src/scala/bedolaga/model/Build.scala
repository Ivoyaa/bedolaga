package bedolaga.model

import bedolaga.model.plan.{State, Step}

final case class Build (state: State, executionPlan: List[Step])
