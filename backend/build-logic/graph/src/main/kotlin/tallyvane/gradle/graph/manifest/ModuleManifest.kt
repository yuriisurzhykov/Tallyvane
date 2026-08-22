package tallyvane.gradle.graph.manifest

internal data class ModuleManifest(
    val layerAllow: Map<String, List<String>>,
    val platformDepends: Map<String, List<String>>,
    val modules: Map<String, FeatureManifest>,
    val planned: Set<String>,
)
