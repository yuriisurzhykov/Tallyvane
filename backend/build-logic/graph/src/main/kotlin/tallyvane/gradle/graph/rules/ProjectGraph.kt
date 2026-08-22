package tallyvane.gradle.graph.rules

internal data class ProjectGraph(
    val included: Set<String>,
    val dependencies: Map<String, Set<String>>,
    val coordinates: List<DeclaredCoordinate>,
)
