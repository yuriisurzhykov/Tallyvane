package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
    override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant) = "complete $factors $authenticatedAt"

    override fun restricted(factors: Set<FactorKind>, authenticatedAt: Instant, toSetUp: Set<FactorKind>) =
        "restricted $factors $authenticatedAt $toSetUp"

    override fun awaiting(accepted: Set<FactorKind>) = "awaiting $accepted"

    override fun paused(accepted: Set<FactorKind>, until: Instant) = "paused $accepted $until"

    override fun exhausted() = "exhausted"

    override fun expired() = "expired"
}

class ProgressSpec :
    StringSpec(
        {
            "each case reports itself to the matching method, with exactly what it carries" {
                Progress.Complete(setOf(Google, Totp), AT).reportTo(Transcript) shouldBe
                    "complete [Google, Totp] $AT"
                Progress.Restricted(setOf(Google), AT, setOf(Totp, RecoveryCode)).reportTo(Transcript) shouldBe
                    "restricted [Google] $AT [Totp, RecoveryCode]"
                Progress.Awaiting(setOf(Totp, RecoveryCode)).reportTo(Transcript) shouldBe
                    "awaiting [Totp, RecoveryCode]"
                Progress.Paused(setOf(Totp), UNTIL).reportTo(Transcript) shouldBe "paused [Totp] $UNTIL"
                Progress.Exhausted().reportTo(Transcript) shouldBe "exhausted"
                Progress.Expired().reportTo(Transcript) shouldBe "expired"
            }
        },
    )
