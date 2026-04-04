package bedolaga.structure.model

import coursier.core.{Configuration, Publication}
import coursier.{Module, ModuleName, Organization, Dependency => CoursierDependency}

final case class Dependency (org: String, module: String, version: String) {
  def asCoursierDependency = new CoursierDependency(
    module = new Module(
      Organization(org),
      ModuleName(module),
      Map.empty
    ),
    version = version,
    configuration = Configuration.empty,
    minimizedExclusions = Set.empty[(Organization, ModuleName)],
    publication = Publication.empty,
    optional = false,
    transitive = true
  )
}
