package tallyvane.gradle.graph.manifest

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class YamlManifestSourceSpec : StringSpec({
    "reads platform depends and planned keys" {
        val manifest =
            YamlManifestSource(
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
            ).load()
        manifest.platformDepends shouldBe
            mapOf(
                "kernel" to emptyList(),
                "events" to listOf("platform:kernel"),
            )
        manifest.planned shouldBe setOf("jobs")
        manifest.modules shouldBe emptyMap()
    }

    "reads a live feature module" {
        val manifest =
            YamlManifestSource(
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
            ).load()
        manifest.modules.getValue("jobs").layers shouldBe listOf("domain")
        manifest.modules.getValue("jobs").reads shouldBe listOf("identity")
    }
})
