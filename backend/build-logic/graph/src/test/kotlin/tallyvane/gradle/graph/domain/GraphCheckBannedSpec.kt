package tallyvane.gradle.graph.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GraphCheckBannedSpec :
    StringSpec({
        "flags mockk and nested mockito groups" {
            val yaml = ModulesYamlFake()
            val projects =
                IncludedProjectsFake(
                    paths = setOf(":jobs"),
                    dependencies = emptyMap(),
                    coordinates =
                        listOf(
                            IncludedProjects.Coordinate(":jobs", "io.mockk"),
                            IncludedProjects.Coordinate(":jobs", "org.mockito.kotlin"),
                            IncludedProjects.Coordinate(":jobs", "org.jetbrains.kotlin"),
                        ),
                )
            GraphCheck.Banned().findings(yaml, projects) shouldBe
                listOf(
                    Finding("Banned coordinate io.mockk in :jobs"),
                    Finding("Banned coordinate org.mockito.kotlin in :jobs"),
                )
        }
    })
