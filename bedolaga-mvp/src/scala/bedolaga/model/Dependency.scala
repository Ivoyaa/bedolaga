package bedolaga.model

import bedolaga.Build.project
import coursier.{Dependency => CoursierDependency, Module, ModuleName, Organization}
import coursier.core.{Configuration, DependencyManagement, MinimizedExclusions, Publication}
import coursier.{Dependency, Fetch, Module, ModuleName, Organization}
import coursier.core.{Configuration, Publication}

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
    transitive = false
  )
}
