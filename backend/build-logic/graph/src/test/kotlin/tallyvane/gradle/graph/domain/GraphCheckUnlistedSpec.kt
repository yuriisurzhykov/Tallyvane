package tallyvane.gradle.graph.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class GraphCheckUnlistedSpec :
    StringSpec({
        // The hole this check exists for: Planned reports a name only when it IS
        // under planned:, and Features iterates what modules: declares, so a
        // capability in neither list was invisible to both.
        "a capability in neither list is a finding" {
            GraphCheck.Unlisted
                .findings(
                    ModulesYamlFake(planned = setOf("jobs"), features = listOf(feature("identity"))),
                    IncludedProjectsFake(
                        paths = setOf(":modules:briefing:domain"),
                        dependencies = emptyMap(),
                    ),
                ).shouldBe(
                    listOf(
                        Finding(
                            "Module briefing is included under :modules: but is in neither " +
                                "modules: nor planned: in modules.yaml",
                        ),
                    ),
                )
        }

        // The manifest here is internally consistent - everything it declares exists,
        // so neither of the other checks has anything to say - and `briefing` still
        // draws an edge nothing governs.
        "the other two checks stay silent on it, which is why this one exists" {
            val yaml = ModulesYamlFake(planned = setOf("jobs"))
            val projects =
                IncludedProjectsFake(
                    paths = setOf(":modules:briefing:domain"),
                    dependencies = mapOf(":modules:briefing:domain" to setOf(":platform:http")),
                )

            GraphCheck.Planned.findings(yaml, projects).shouldBeEmpty()
            GraphCheck.Features.findings(yaml, projects).shouldBeEmpty()
        }

        // One finding per capability, not per layer: five layers of one undeclared
        // module is one thing to fix, and five copies of it would bury the rest.
        "reports a capability once however many of its layers are included" {
            GraphCheck.Unlisted
                .findings(
                    ModulesYamlFake(),
                    IncludedProjectsFake(
                        paths =
                            setOf(
                                ":modules:briefing:domain",
                                ":modules:briefing:application",
                                ":modules:briefing:web",
                            ),
                        dependencies = emptyMap(),
                    ),
                ).size shouldBe 1
        }

        "a declared capability is clean, from either list" {
            GraphCheck.Unlisted
                .findings(
                    ModulesYamlFake(planned = setOf("jobs"), features = listOf(feature("identity"))),
                    IncludedProjectsFake(
                        paths = setOf(":modules:identity:domain", ":modules:jobs:contract"),
                        dependencies = emptyMap(),
                    ),
                ).shouldBeEmpty()
        }

        "paths outside :modules: are not this check's business" {
            GraphCheck.Unlisted
                .findings(
                    ModulesYamlFake(),
                    IncludedProjectsFake(
                        paths = setOf(":platform:kernel", ":server", ":playground:isolation"),
                        dependencies = emptyMap(),
                    ),
                ).shouldBeEmpty()
        }
    })

private fun feature(name: String): Feature = Feature(name, layers = listOf("domain"), reads = emptyList())
