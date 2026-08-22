package tallyvane.gradle.graph.domain

/**
 * [IncludedProjects] whose maps are passed in, for Kotest specs.
 *
 * Lives in `src/test` so it does not ship in the plugin jar (ADR-044).
 *
 * @param paths Leaf Gradle paths; becomes [IncludedProjects.paths].
 * @param dependencies Leaf path to compile project-dependency paths.
 *   [IncludedProjects.dependencies] returns empty for a missing key.
 * @param coordinates External modules for [GraphCheck.Banned]; default none.
 */
internal class IncludedProjectsFake(
    private val paths: Set<String>,
    private val dependencies: Map<String, Set<String>>,
    private val coordinates: List<IncludedProjects.Coordinate> = emptyList(),
) : IncludedProjects {
    override fun paths(): Set<String> = paths

    override fun dependencies(path: String): Set<String> = dependencies[path].orEmpty()

    override fun coordinates(): List<IncludedProjects.Coordinate> = coordinates
}
