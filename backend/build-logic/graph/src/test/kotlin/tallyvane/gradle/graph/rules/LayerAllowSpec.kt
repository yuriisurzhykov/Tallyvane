package tallyvane.gradle.graph.rules

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.gradle.graph.manifest.FeatureManifest
import tallyvane.gradle.graph.manifest.ModuleManifest

class LayerAllowSpec : StringSpec({
    "own layer is required only when the feature has that layer" {
        val allow =
            LayerAllow(
                manifest =
                    baseManifest(
                        layerAllow = mapOf("application" to listOf("own:domain", "own:contract")),
                    ),
                module = "jobs",
                feature = FeatureManifest(layers = listOf("domain"), reads = emptyList()),
            )
        allow.required("application") shouldBe setOf(":modules:jobs:domain")
    }

    "any:contract expands to each read's contract module" {
        val allow =
            LayerAllow(
                manifest = baseManifest(layerAllow = mapOf("application" to listOf("any:contract"))),
                module = "briefing",
                feature = FeatureManifest(layers = listOf("application"), reads = listOf("jobs", "contacts")),
            )
        allow.allowed("application") shouldBe
            setOf(":modules:jobs:contract", ":modules:contacts:contract")
    }

    "platform star expands to every platform module" {
        val allow =
            LayerAllow(
                manifest =
                    baseManifest(
                        layerAllow = mapOf("infrastructure" to listOf("platform:*")),
                        platformDepends =
                            mapOf(
                                "kernel" to emptyList(),
                                "persistence" to listOf("platform:kernel"),
                            ),
                    ),
                module = "jobs",
                feature = FeatureManifest(layers = listOf("infrastructure"), reads = emptyList()),
            )
        allow.allowed("infrastructure") shouldBe setOf(":platform:kernel", ":platform:persistence")
    }
})

private fun baseManifest(
    layerAllow: Map<String, List<String>> = mapOf("domain" to listOf("platform:kernel")),
    platformDepends: Map<String, List<String>> = mapOf("kernel" to emptyList()),
): ModuleManifest =
    ModuleManifest(
        layerAllow = layerAllow,
        platformDepends = platformDepends,
        modules = emptyMap(),
        planned = emptySet(),
    )
