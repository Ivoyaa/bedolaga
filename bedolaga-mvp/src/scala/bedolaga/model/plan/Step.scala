package bedolaga.model.plan

sealed trait Step

case object End extends Step

case object Fetch extends Step

case object ScalaCompile extends Step

case object Package extends Step

case object RunApp extends Step
