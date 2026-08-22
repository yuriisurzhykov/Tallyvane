package tallyvane.gradle.graph.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import tallyvane.gradle.graph.domain.Feature
import tallyvane.gradle.graph.domain.Finding
import tallyvane.gradle.graph.domain.IncludedProjects
import tallyvane.gradle.graph.domain.IncludedProjectsFake
import tallyvane.gradle.graph.domain.ModulesYaml
import tallyvane.gradle.graph.domain.ModulesYamlFake
import tallyvane.gradle.graph.domain.Platform

class ValidateModuleGraphSpec :
    StringSpec({
        "empty platform graph matching the manifest is clean" {
            ValidateModuleGraph(platformOnly(), kernelOnly()).findings().shouldBeEmpty()
        }

        "planned module included in Gradle is a finding" {
            ValidateModuleGraph(
                platformOnly(planned = setOf("jobs")),
                IncludedProjectsFake(
                    paths = setOf(":platform:kernel", ":modules:jobs:domain"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                ),
            ).findings()
                .shouldContain(
                    Finding(
                        "Planned module jobs is included as :modules:jobs:domain — " +
                            "move it from planned: to modules: in modules.yaml",
                    ),
                )
        }

        "undeclared feature edge is a finding" {
            ValidateModuleGraph(jobsDomain(), jobsWithoutKernel())
                .findings()
                .shouldContain(Finding("Undeclared dependency: :modules:jobs:domain -> :platform:http"))
        }

        "missing required platform edge on a feature is a finding" {
            ValidateModuleGraph(
                jobsDomain(),
                IncludedProjectsFake(
                    paths = setOf(":platform:kernel", ":modules:jobs:domain"),
                    dependencies =
                        mapOf(
                            ":platform:kernel" to emptySet(),
                            ":modules:jobs:domain" to emptySet(),
                        ),
                ),
            ).findings()
                .shouldContain(
                    Finding("Declared but unused dependency: :modules:jobs:domain -> :platform:kernel"),
                )
        }

        "mockk coordinate is a finding" {
            ValidateModuleGraph(
                platformOnly(),
                IncludedProjectsFake(
                    paths = setOf(":platform:kernel"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    coordinates = listOf(IncludedProjects.Coordinate(":arch-tests", "io.mockk")),
                ),
            ).findings()
                .shouldContain(Finding("Banned coordinate io.mockk in :arch-tests"))
        }
    })

private fun platformOnly(planned: Set<String> = emptySet()): ModulesYaml =
    ModulesYamlFake(
        planned = planned,
        platforms = listOf(Platform("kernel", emptyList())),
        layerAllow = mapOf("domain" to listOf("platform:kernel")),
    )

private fun jobsDomain(): ModulesYaml =
    ModulesYamlFake(
        platforms = listOf(Platform("kernel", emptyList())),
        features = listOf(Feature("jobs", listOf("domain"), emptyList())),
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
