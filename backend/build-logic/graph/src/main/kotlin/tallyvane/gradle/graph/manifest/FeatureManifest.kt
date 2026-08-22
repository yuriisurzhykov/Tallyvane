package tallyvane.gradle.graph.manifest

internal data class FeatureManifest(
    val layers: List<String>,
    val reads: List<String>,
)
