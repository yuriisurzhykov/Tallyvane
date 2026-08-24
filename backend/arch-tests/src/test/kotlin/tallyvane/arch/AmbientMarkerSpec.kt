package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * `ArchitectureRulesSpec` asserts only that a rule's fixture directory is not
 * clean, which cannot say *which* marker fired. Kotlin 2.4 put three new
 * ambient sources in the standard library — `Uuid.random`, `Uuid.generateV4`
 * and `Uuid.generateV7` — and a marker nobody has seen fire is the same
 * unverified claim as a rule nobody has seen fail.
 */
class AmbientMarkerSpec :
    StringSpec(
        {
            val flagged = { noAmbientRandom(fixtureScope("no-ambient-random")).joinToString() }

            "no-ambient-random flags Uuid.random(), new in the 2.4 standard library" {
                flagged() shouldContain "DirtyUuid.kt"
            }

            "no-ambient-random flags Uuid.generateV7(), so no time marker has to" {
                flagged() shouldContain "DirtyUuidV7.kt"
            }

            "the v7 marker is broad on purpose and also catches the NonMonotonicAt form" {
                val handedTheInstant = "fun id(at: Instant): Uuid = Uuid.generateV7NonMonotonicAt(at)"

                ambientRandomMarkersIn(handedTheInstant) shouldBe listOf("Uuid.generateV7")
            }
        },
    )
