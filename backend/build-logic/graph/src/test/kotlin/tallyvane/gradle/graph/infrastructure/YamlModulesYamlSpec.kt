package tallyvane.gradle.graph.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class YamlModulesYamlSpec :
    StringSpec({
        "reads platform depends and planned keys" {
            val manifest =
                YamlModulesYaml(
                    text =
                        """
                        layers:
                          domain: [platform:kernel]
                        platform:
                          kernel: { depends: [] }
                          events: { depends: [platform:kernel] }
                        modules: {}
                        planned:
                          jobs:
                            layers: [domain]
                            reads: []
                        """.trimIndent(),
                    origin = "modules.yaml",
                )
            val platforms = manifest.platforms().associateBy { platform -> platform.name }
            platforms.getValue("kernel").expectedPaths() shouldBe emptyList()
            platforms.getValue("events").expectedPaths() shouldBe listOf(":platform:kernel")
            manifest.planned() shouldBe setOf("jobs")
            manifest.features() shouldBe emptyList()
        }

        "reads a live feature module" {
            val manifest =
                YamlModulesYaml(
                    text =
                        """
                        layers:
                          domain: [platform:kernel]
                        platform:
                          kernel: { depends: [] }
                        modules:
                          jobs:
                            layers: [domain]
                            reads: [identity]
                        """.trimIndent(),
                    origin = "modules.yaml",
                )
            val jobs = manifest.features().single()
            jobs.name shouldBe "jobs"
            jobs.layers shouldBe listOf("domain")
            jobs.readContractPaths() shouldBe listOf(":modules:identity:contract")
        }
    })
