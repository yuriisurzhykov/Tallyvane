package tallyvane.gradle.graph.rules

import tallyvane.gradle.graph.manifest.FeatureManifest
import tallyvane.gradle.graph.manifest.ModuleManifest

internal class FeatureGraph {
    fun findings(
        manifest: ModuleManifest,
        graph: ProjectGraph,
    ): List<GraphFinding> =
        manifest.modules.flatMap { (name, feature) ->
            feature.layers.flatMap { layer ->
                Layer(manifest, name, feature, layer).findings(graph)
            }
        }

    private class Layer(
        manifest: ModuleManifest,
        private val name: String,
        feature: FeatureManifest,
        private val layer: String,
    ) {
        private val allow = LayerAllow(manifest, name, feature)
        private val path = GradleModulePath.feature(name, layer).value

        fun findings(graph: ProjectGraph): List<GraphFinding> {
            if (path !in graph.included) {
                return listOf(
                    GraphFinding(
                        "modules.yaml modules.$name layer $layer is missing from the Gradle graph ($path)",
                    ),
                )
            }
            val allowed = allow.allowed(layer)
            val got = graph.dependencies[path].orEmpty()
            val extra = (got - allowed).map { GraphFinding("Undeclared dependency: $path -> $it") }
            val missing =
                (allow.required(layer) - got).map { GraphFinding("Declared but unused dependency: $path -> $it") }
            return extra + missing
        }
    }
}
