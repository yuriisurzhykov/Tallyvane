package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tallyvane.authentication.domain.TotpEnrollment.Standing
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant

private val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)

// RFC 4226 appendix D's code for counter 1 and counter 2, at times inside their 30-second steps.
private const val CODE_AT_STEP_1 = "287082"
private const val CODE_AT_STEP_2 = "359152"
private val IN_STEP_1 = Instant.fromEpochSeconds(45)
private val IN_STEP_2 = Instant.fromEpochSeconds(75)

private fun TotpEnrollment.told(): Triple<String, Standing, Long?> {
    val told = mutableListOf<Triple<String, Standing, Long?>>()
    writeTo { seed, standing, last -> told += Triple(seed.revealed(), standing, last) }
    return told.single()
}

private fun TotpEnrollment.active(): TotpEnrollment = confirm(CODE_AT_STEP_1, IN_STEP_1).reportTo(Accepting())

private class Accepting : CodeVerdict.Report<TotpEnrollment> {
    override fun accepted(next: TotpEnrollment): TotpEnrollment = next

    override fun wrong(): TotpEnrollment = error("The code should have been accepted.")
}

private fun CodeVerdict.isAccepted(): Boolean = reportTo(
    object : CodeVerdict.Report<Boolean> {
        override fun accepted(next: TotpEnrollment): Boolean = true

        override fun wrong(): Boolean = false
    },
)

class TotpEnrollmentSpec :
    StringSpec(
        {
            "a new enrolment is pending and counts for nothing" {
                val begun = TotpEnrollment.begin(ENTROPY)

                begun.isActive() shouldBe false
                begun.told().second shouldBe Standing.Pending
                begun.told().third shouldBe null
            }

            "a seed of too few bytes is refused" {
                shouldThrow<IllegalArgumentException> { TotpEnrollment.begin(ByteArray(19)) }
            }

            "the first right code makes it active and remembers its step" {
                val active = TotpEnrollment.begin(ENTROPY).active()

                active.isActive() shouldBe true
                active.told().third shouldBe 1L
            }

            "a wrong first code leaves it pending, as a code that is not six digits does" {
                val begun = TotpEnrollment.begin(ENTROPY)

                begun.confirm("000000", IN_STEP_1).isAccepted() shouldBe false
                begun.confirm("28708", IN_STEP_1).isAccepted() shouldBe false
                begun.confirm(" 287082", IN_STEP_1).isAccepted() shouldBe false
            }

            "a pending enrolment checks no code, since it is not a factor yet" {
                TotpEnrollment.begin(ENTROPY).check(CODE_AT_STEP_1, IN_STEP_1).isAccepted() shouldBe false
            }

            "an active one accepts a later step and then refuses a step it already took" {
                val active = TotpEnrollment.begin(ENTROPY).active()

                val later = active.check(CODE_AT_STEP_2, IN_STEP_2).reportTo(Accepting())

                later.told().third shouldBe 2L
                later.check(CODE_AT_STEP_2, IN_STEP_2).isAccepted() shouldBe false
                later.check(CODE_AT_STEP_1, IN_STEP_2).isAccepted() shouldBe false
            }

            "the code that confirmed it cannot be used again to sign in" {
                TotpEnrollment.begin(ENTROPY).active().check(CODE_AT_STEP_1, IN_STEP_1).isAccepted() shouldBe false
            }

            "retiring an active one stops its codes, and retiring anything else changes nothing" {
                val active = TotpEnrollment.begin(ENTROPY).active()
                val retired = active.retired()

                retired.told().second shouldBe Standing.Retired
                retired.isActive() shouldBe false
                retired.check(CODE_AT_STEP_2, IN_STEP_2).isAccepted() shouldBe false
                retired.retired().told() shouldBe retired.told()
                TotpEnrollment.begin(ENTROPY).retired().told().second shouldBe Standing.Pending
            }

            "it provisions an app with the key and an address that carries it" {
                val shown = mutableListOf<Pair<String, String>>()
                TotpEnrollment.begin(ENTROPY).provision("Tallyvane") { key, uri ->
                    shown += key.revealed() to uri.revealed()
                }

                val (key, uri) = shown.single()
                key shouldBe "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                uri shouldBe "otpauth://totp/Tallyvane?secret=$key&issuer=Tallyvane&algorithm=SHA1&digits=6&period=30"
            }

            "an issuer with a space is written as the address needs it" {
                val shown = mutableListOf<String>()
                TotpEnrollment.begin(ENTROPY).provision("Tally Vane") { _, uri -> shown += uri.revealed() }

                shown.single() shouldContain "otpauth://totp/Tally%20Vane?"
            }

            "printing one tells the standing and never the seed" {
                TotpEnrollment.begin(ENTROPY).toString() shouldNotContain "GEZDG"
            }

            "what it tells comes back as the same enrolment" {
                val active = TotpEnrollment.begin(ENTROPY).active()

                val restored = TotpEnrollment.restore { record ->
                    active.writeTo { seed, standing, last -> record.kept(seed, standing, last) }
                }

                restored.told() shouldBe active.told()
                restored.check(CODE_AT_STEP_2, IN_STEP_2).isAccepted() shouldBe true
            }

            "a replay no enrolment could have told is refused" {
                val seed = Secret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")

                shouldThrow<IllegalStateException> { TotpEnrollment.restore { } }
                shouldThrow<IllegalStateException> {
                    TotpEnrollment.restore { record ->
                        record.kept(seed, Standing.Pending, null)
                        record.kept(seed, Standing.Pending, null)
                    }
                }
                shouldThrow<IllegalStateException> { TotpEnrollment.restore { it.kept(seed, Standing.Pending, 4L) } }
                shouldThrow<IllegalStateException> { TotpEnrollment.restore { it.kept(seed, Standing.Active, null) } }
                shouldThrow<IllegalStateException> {
                    TotpEnrollment.restore { it.kept(Secret("not base32 1"), Standing.Active, 4L) }
                }
            }
        },
    )
