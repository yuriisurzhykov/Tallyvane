package tallyvane.gradle.graph.rules

import tallyvane.gradle.graph.manifest.ModuleManifest

internal class PlatformGraph {
    fun findings(
        manifest: ModuleManifest,
        graph: ProjectGraph,
    ): List<GraphFinding> =
        manifest.platformDepends.flatMap { (name, expectedNames) ->
            Node(name, expectedNames).findings(graph)
        }

    private class Node(
        private val name: String,
        private val expectedNames: List<String>,
    ) {
        fun findings(graph: ProjectGraph): List<GraphFinding> {
            val path = GradleModulePath.platform(name).value
            val expected = expectedNames.map { GradleModulePath.platform(it).value }.sorted()
            val got = graph.dependencies[path].orEmpty().sorted()
            return when {
                path !in graph.included ->
                    listOf(GraphFinding("modules.yaml platform.$name is missing from the Gradle graph ($path)"))
                got == expected -> emptyList()
                else -> listOf(mismatch(path, expected, got))
            }
        }

        private fun mismatch(
            path: String,
            expected: List<String>,
            got: List<String>,
        ): GraphFinding {
            val extra = got - expected.toSet()
            val missing = expected - got.toSet()
            val extraText = if (extra.isEmpty()) "" else " Extra: $extra."
            val missingText = if (missing.isEmpty()) "" else " Missing: $missing."
            return GraphFinding("Platform graph mismatch for $path.$extraText$missingText")
        }
    }
}
