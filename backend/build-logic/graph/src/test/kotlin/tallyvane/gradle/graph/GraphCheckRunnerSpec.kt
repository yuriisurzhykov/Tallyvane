package tallyvane.gradle.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain

class GraphCheckRunnerSpec :
    StringSpec({
        "empty platform graph matching the manifest is clean" {
            GraphCheckRunner.Base(platformOnly(), kernelOnly()).runAll().shouldBeEmpty()
        }

        "planned module included in Gradle is a finding" {
            GraphCheckRunner
                .Base(
                    platformOnly(planned = setOf("jobs")),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":modules:jobs:domain"),
                        dependencies = mapOf(":platform:kernel" to emptySet()),
                    ),
                ).runAll()
                .shouldContain(
                    Finding(
                        "Planned module jobs is included as :modules:jobs:domain — " +
                            "move it from planned: to modules: in modules.yaml",
                    ),
                )
        }

        "undeclared feature edge is a finding" {
            GraphCheckRunner
                .Base(jobsDomain(), jobsWithoutKernel())
                .runAll()
                .shouldContain(Finding("Undeclared dependency: :modules:jobs:domain -> :platform:http"))
        }

        "missing required platform edge on a feature is a finding" {
            GraphCheckRunner
                .Base(
                    jobsDomain(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":modules:jobs:domain"),
                        dependencies =
                            mapOf(
                                ":platform:kernel" to emptySet(),
                                ":modules:jobs:domain" to emptySet(),
                            ),
                    ),
                ).runAll()
                .shouldContain(
                    Finding("Declared but unused dependency: :modules:jobs:domain -> :platform:kernel"),
                )
        }

        "mockk coordinate is a finding" {
            GraphCheckRunner
                .Base(
                    platformOnly(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel"),
                        dependencies = mapOf(":platform:kernel" to emptySet()),
                        coordinates = listOf(IncludedProjects.Coordinate(":arch-tests", "io.mockk")),
                    ),
                ).runAll()
                .shouldContain(Finding("Banned coordinate io.mockk in :arch-tests"))
        }
    })

private fun platformOnly(planned: Set<String> = emptySet()): ModulesYaml =
    ModulesYamlFake(
        planned = planned,
        platforms = listOf(Platform.FromManifest("kernel", emptyList())),
        layerAllow = mapOf("domain" to listOf("platform:kernel")),
    )

private fun jobsDomain(): ModulesYaml =
    ModulesYamlFake(
        platforms = listOf(Platform.FromManifest("kernel", emptyList())),
        features = listOf(Feature.FromManifest("jobs", listOf("domain"), emptyList())),
        layerAllow = mapOf("domain" to listOf("platform:kernel")),
    )

private fun kernelOnly(): IncludedProjects =
    IncludedProjectsFake(
        paths = setOf(":platform:kernel"),
        dependencies = mapOf(":platform:kernel" to emptySet()),
    )

private fun jobsWithoutKernel(): IncludedProjects =
    IncludedProjectsFake(
        paths = setOf(":platform:kernel", ":modules:jobs:domain"),
        dependencies =
            mapOf(
                ":platform:kernel" to emptySet(),
                ":modules:jobs:domain" to setOf(":platform:http"),
            ),
    )
