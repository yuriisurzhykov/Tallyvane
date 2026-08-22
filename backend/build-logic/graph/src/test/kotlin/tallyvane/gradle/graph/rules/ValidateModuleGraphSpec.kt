package tallyvane.gradle.graph.rules

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import tallyvane.gradle.graph.manifest.FeatureManifest
import tallyvane.gradle.graph.manifest.ModuleManifest

class ValidateModuleGraphSpec : StringSpec({
    "empty platform graph matching the manifest is clean" {
        val findings =
            ValidateModuleGraph().invoke(
                platformOnly(),
                ProjectGraph(
                    included = setOf(":platform:kernel"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldBeEmpty()
    }

    "planned module included in Gradle is a finding" {
        val findings =
            ValidateModuleGraph().invoke(
                platformOnly().copy(planned = setOf("jobs")),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":modules:jobs:domain"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(
            GraphFinding(
                "Planned module jobs is included as :modules:jobs:domain — " +
                    "move it from planned: to modules: in modules.yaml",
            ),
        )
    }

    "undeclared feature edge is a finding" {
        val findings =
            ValidateModuleGraph().invoke(
                jobsDomain(),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":modules:jobs:domain"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":modules:jobs:domain" to setOf(":platform:http"),
                        ),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(GraphFinding("Undeclared dependency: :modules:jobs:domain -> :platform:http"))
    }

    "missing required platform edge on a feature is a finding" {
        val findings =
            ValidateModuleGraph().invoke(
                jobsDomain(),
                ProjectGraph(
                    included = setOf(":platform:kernel", ":modules:jobs:domain"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":modules:jobs:domain" to emptySet(),
                        ),
                    coordinates = emptyList(),
                ),
            )
        findings.shouldContain(
            GraphFinding("Declared but unused dependency: :modules:jobs:domain -> :platform:kernel"),
        )
    }

    "mockk coordinate is a finding" {
        val findings =
            ValidateModuleGraph().invoke(
                platformOnly(),
                ProjectGraph(
                    included = setOf(":platform:kernel"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    coordinates = listOf(DeclaredCoordinate(":arch-tests", "io.mockk")),
                ),
            )
        findings.shouldContain(GraphFinding("Banned coordinate io.mockk in :arch-tests"))
    }
})

private fun platformOnly(): ModuleManifest =
    ModuleManifest(
        layerAllow = mapOf("domain" to listOf("platform:kernel")),
        platformDepends = mapOf("kernel" to emptyList()),
        modules = emptyMap(),
        planned = emptySet(),
    )

private fun jobsDomain(): ModuleManifest =
    platformOnly().copy(
        modules =
            mapOf(
                "jobs" to FeatureManifest(layers = listOf("domain"), reads = emptyList()),
            ),
    )
