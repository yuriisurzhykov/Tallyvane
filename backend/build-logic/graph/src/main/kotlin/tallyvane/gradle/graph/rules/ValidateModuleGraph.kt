package tallyvane.gradle.graph.rules

import tallyvane.gradle.graph.manifest.ModuleManifest

internal class ValidateModuleGraph {
    fun invoke(
        manifest: ModuleManifest,
        graph: ProjectGraph,
    ): List<GraphFinding> =
        PlannedModules().findings(manifest.planned, graph.included) +
            PlatformGraph().findings(manifest, graph) +
            FeatureGraph().findings(manifest, graph) +
            BannedCoordinates().findings(graph.coordinates)
}
