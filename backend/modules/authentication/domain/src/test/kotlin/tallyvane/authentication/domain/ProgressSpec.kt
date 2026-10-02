package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.RecoveryCode
import tallyvane.authentication.domain.FactorKind.Totp
import kotlin.time.Instant

private val AT = Instant.parse("2026-10-01T09:00:10Z")
private val UNTIL = Instant.parse("2026-10-01T09:00:20Z")

/**
 * Writes down which method it was called with and what it was given, so a test can see that each
 * case hands over its own contents and nothing else.
 */
private object Transcript : Progress.Report<String> {
    override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String) =
        "complete $factors $authenticatedAt $subject"

    override fun restricted(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String, toSetUp: Set<FactorKind>) =
        "restricted $factors $authenticatedAt $subject $toSetUp"

    override fun awaiting(accepted: Set<FactorKind>) = "awaiting $accepted"

    override fun paused(accepted: Set<FactorKind>, until: Instant) = "paused $accepted $until"

    override fun exhausted() = "exhausted"

    override fun expired() = "expired"
}

class ProgressSpec :
    StringSpec(
        {
            "each case reports itself to the matching method, with exactly what it carries" {
                Progress.Complete(setOf(Google, Totp), AT, "sub-1").reportTo(Transcript) shouldBe
                    "complete [Google, Totp] $AT sub-1"
                Progress.Restricted(setOf(Google), AT, "sub-1", setOf(Totp, RecoveryCode)).reportTo(Transcript) shouldBe
                    "restricted [Google] $AT sub-1 [Totp, RecoveryCode]"
                Progress.Awaiting(setOf(Totp, RecoveryCode)).reportTo(Transcript) shouldBe
                    "awaiting [Totp, RecoveryCode]"
                Progress.Paused(setOf(Totp), UNTIL).reportTo(Transcript) shouldBe "paused [Totp] $UNTIL"
                Progress.Exhausted().reportTo(Transcript) shouldBe "exhausted"
                Progress.Expired().reportTo(Transcript) shouldBe "expired"
            }

            "a complete or restricted attempt printed in a log does not name the account" {
                Progress.Complete(setOf(Google), AT, "sub-1").toString() shouldNotContain "sub-1"
                Progress.Restricted(setOf(Google), AT, "sub-1", setOf(Totp)).toString() shouldNotContain "sub-1"
            }
        },
    )
