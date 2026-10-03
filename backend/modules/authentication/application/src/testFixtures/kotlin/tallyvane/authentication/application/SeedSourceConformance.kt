package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.authentication.application.port.SeedSource
import tallyvane.authentication.domain.TotpEnrollment

/**
 * The behaviour every [SeedSource] must show, whatever makes the bytes.
 */
abstract class SeedSourceConformance : StringSpec() {
    protected abstract fun fresh(): SeedSource

    init {
        "gives twenty bytes, which is what an enrolment is made of" {
            val seed = fresh().next()

            seed.size shouldBe 20
            TotpEnrollment.begin(seed).isPending() shouldBe true
        }

        "never gives the same seed twice" {
            val source = fresh()

            source.next().toList() shouldNotBe source.next().toList()
        }
    }
}
