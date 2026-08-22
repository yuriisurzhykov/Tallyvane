package tallyvane.gradle.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GraphCheckFeaturesAllowSpec :
    StringSpec({
        "own layer is required only when the feature has that layer" {
            val allow =
                GraphCheck.Features.Allow(
                    yaml =
                        ModulesYamlFake(
                            layerAllow = mapOf("application" to listOf("own:domain", "own:contract")),
                        ),
                    feature = Feature.FromManifest("jobs", listOf("domain"), emptyList()),
                )
            allow.required("application") shouldBe setOf(":modules:jobs:domain")
        }

        "any:contract expands to each read's contract module" {
            val allow =
                GraphCheck.Features.Allow(
                    yaml = ModulesYamlFake(layerAllow = mapOf("application" to listOf("any:contract"))),
                    feature = Feature.FromManifest("briefing", listOf("application"), listOf("jobs", "contacts")),
                )
            allow.allowed("application") shouldBe
                setOf(":modules:jobs:contract", ":modules:contacts:contract")
        }

        "platform star expands to every platform module" {
            val allow =
                GraphCheck.Features.Allow(
                    yaml =
                        ModulesYamlFake(
                            platforms =
                                listOf(
                                    Platform.FromManifest("kernel", emptyList()),
                                    Platform.FromManifest("persistence", listOf("platform:kernel")),
                                ),
                            layerAllow = mapOf("infrastructure" to listOf("platform:*")),
                        ),
                    feature = Feature.FromManifest("jobs", listOf("infrastructure"), emptyList()),
                )
            allow.allowed("infrastructure") shouldBe setOf(":platform:kernel", ":platform:persistence")
        }
    })
