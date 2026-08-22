package tallyvane.gradle.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain

class GraphCheckPlatformSpec :
    StringSpec({
        "extra platform edge is a finding" {
            GraphCheck.Platforms
                .findings(
                    twoPlatforms(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":platform:events"),
                        dependencies =
                            mapOf(
                                ":platform:kernel" to emptySet(),
                                ":platform:events" to setOf(":platform:kernel", ":platform:http"),
                            ),
                    ),
                ).shouldContain(
                    Finding("Platform graph mismatch for :platform:events. Extra: [:platform:http]."),
                )
        }

        "declared platform edge that is unused is a finding" {
            GraphCheck.Platforms
                .findings(
                    twoPlatforms(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":platform:events"),
                        dependencies =
                            mapOf(
                                ":platform:kernel" to emptySet(),
                                ":platform:events" to emptySet(),
                            ),
                    ),
                ).shouldContain(
                    Finding("Platform graph mismatch for :platform:events. Missing: [:platform:kernel]."),
                )
        }

        "yaml platform module missing from Gradle is a finding" {
            GraphCheck.Platforms
                .findings(
                    twoPlatforms(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel"),
                        dependencies = mapOf(":platform:kernel" to emptySet()),
                    ),
                ).shouldContain(
                    Finding(
                        "modules.yaml platform.events is missing from the Gradle graph (:platform:events)",
                    ),
                )
        }

        "matching platform graph is clean" {
            GraphCheck.Platforms
                .findings(
                    twoPlatforms(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":platform:events"),
                        dependencies =
                            mapOf(
                                ":platform:kernel" to emptySet(),
                                ":platform:events" to setOf(":platform:kernel"),
                            ),
                    ),
                ).shouldBeEmpty()
        }
    })

private fun twoPlatforms(): ModulesYaml =
    ModulesYamlFake(
        platforms =
            listOf(
                Platform.FromManifest("kernel", emptyList()),
                Platform.FromManifest("events", listOf("platform:kernel")),
            ),
    )
