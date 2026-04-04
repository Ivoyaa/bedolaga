package bedolaga.plan.model

final case class Build (state: State, executionPlan: List[Phase])