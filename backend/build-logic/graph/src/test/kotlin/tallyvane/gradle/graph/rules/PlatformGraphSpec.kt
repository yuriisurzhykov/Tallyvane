package tallyvane.gradle.graph.rules

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import tallyvane.gradle.graph.manifest.ModuleManifest

class PlatformGraphSpec : StringSpec({
    "extra platform edge is a finding" {
        val findings =
            PlatformGraph().findings(
                twoPlatforms(),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":platform:events"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":platform:events" to setOf(":platform:kernel", ":platform:http"),
                        ),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(
            GraphFinding(
                "Platform graph mismatch for :platform:events. Extra: [:platform:http].",
            ),
        )
    }

    "declared platform edge that is unused is a finding" {
        val findings =
            PlatformGraph().findings(
                twoPlatforms(),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":platform:events"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":platform:events" to emptySet(),
                        ),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(
            GraphFinding(
                "Platform graph mismatch for :platform:events. Missing: [:platform:kernel].",
            ),
        )
    }

    "yaml platform module missing from Gradle is a finding" {
        val findings =
            PlatformGraph().findings(
                twoPlatforms(),
                ProjectGraph(
                    included = setOf(":platform:kernel"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(
            GraphFinding("modules.yaml platform.events is missing from the Gradle graph (:platform:events)"),
        )
    }

    "matching platform graph is clean" {
        PlatformGraph()
            .findings(
                twoPlatforms(),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":platform:events"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":platform:events" to setOf(":platform:kernel"),
                        ),
                    coordinates = emptyList(),
                ),
            ).shouldBeEmpty()
    }
})

private fun twoPlatforms(): ModuleManifest =
    ModuleManifest(
        layerAllow = emptyMap(),
        platformDepends =
            mapOf(
                "kernel" to emptyList(),
                "events" to listOf("platform:kernel"),
            ),
        modules = emptyMap(),
        planned = emptySet(),
    )
