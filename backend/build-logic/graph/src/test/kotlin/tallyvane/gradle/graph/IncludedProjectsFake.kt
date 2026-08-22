package tallyvane.gradle.graph

internal class IncludedProjectsFake(
    private val paths: Set<String>,
    private val dependencies: Map<String, Set<String>>,
    private val coordinates: List<IncludedProjects.Coordinate> = emptyList(),
) : IncludedProjects {
    override fun paths(): Set<String> = paths

    override fun dependencies(path: String): Set<String> = dependencies[path].orEmpty()

    override fun coordinates(): List<IncludedProjects.Coordinate> = coordinates
}
