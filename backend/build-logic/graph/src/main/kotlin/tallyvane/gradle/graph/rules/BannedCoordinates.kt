package tallyvane.gradle.graph.rules

internal class BannedCoordinates(
    private val groups: List<String> =
        listOf(
            "io.mockk",
            "org.mockito",
            "org.mockito.kotlin",
        ),
) {
    fun findings(coordinates: List<DeclaredCoordinate>): List<GraphFinding> =
        coordinates.mapNotNull { coordinate ->
            if (isBanned(coordinate.group)) {
                GraphFinding("Banned coordinate ${coordinate.group} in ${coordinate.projectPath}")
            } else {
                null
            }
        }

    private fun isBanned(group: String): Boolean =
        groups.any { group == it || group.startsWith("$it.") }
}
