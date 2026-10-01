package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.VerifiedFactor
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val FIRST = Uuid.parse("0199a000-0000-7000-8000-000000000001")
private val SECOND = Uuid.parse("0199a000-0000-7000-8000-000000000002")

private val START = Instant.parse("2026-10-01T09:00:00Z")

private fun Attempt.verifying(kind: FactorKind, secondsIn: Int) =
    withVerified(VerifiedFactor(kind, START + secondsIn.seconds))

private fun Attempt.failingAt(secondsIn: Int) = withFailure(START + secondsIn.seconds)

/**
 * The behaviour every [Attempts] must show, whatever keeps the attempts.
 *
 * Written once and inherited by the fake the use-case tests substitute and by the adapter over
 * Postgres, so the two cannot quietly disagree (ADR-046).
 *
 * What an attempt is, is judged by what it tells through [Attempt.writeTo], compared as an
 * [AttemptStory]: an attempt has no `equals` and no getters, and a suite that wanted either would be
 * asking for the loophole the design closes.
 */
abstract class AttemptsConformance : StringSpec() {
    /**
     * Keepers with nothing kept, and the transactions their calls run in.
     */
    protected abstract suspend fun fresh(): Subject

    /**
     * The port under test, with the transactions its adapter needs around each call.
     */
    interface Subject {
        val attempts: Attempts
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: Attempts.() -> T): T =
        transactions.inTransaction { Verdict.Commit(attempts.call()) }

    private suspend fun Subject.found(id: Uuid): AttemptStory? = inOwnTransaction { find(id) }?.let { AttemptStory(it) }

    init {
        "finds nothing under an id that was never saved" {
            fresh().found(FIRST) shouldBe null
        }

        "keeps a new attempt and hands back one that tells the same story" {
            val subject = fresh()
            val attempt = Attempt(Purpose.AdminLogin, START)

            subject.inOwnTransaction { save(FIRST, attempt) } shouldBe AttemptSaveOutcome.Saved

            subject.found(FIRST) shouldBe AttemptStory(attempt)
        }

        "keeps every factor and wrong answer, in the order they came" {
            val subject = fresh()
            val attempt = Attempt(Purpose.Login, START)
                .verifying(FactorKind.Google, secondsIn = 10)
                .failingAt(secondsIn = 20)
                .verifying(FactorKind.Totp, secondsIn = 40)
                .failingAt(secondsIn = 30)
                .failingAt(secondsIn = 35)

            subject.inOwnTransaction { save(FIRST, attempt) }

            subject.found(FIRST) shouldBe AttemptStory(attempt)
        }

        "keeps what an attempt gained after it was first saved" {
            val subject = fresh()
            val began = Attempt(Purpose.Login, START)
            val grown = began.verifying(FactorKind.Google, secondsIn = 10).failingAt(secondsIn = 12)

            subject.inOwnTransaction { save(FIRST, began) }

            subject.inOwnTransaction { save(FIRST, grown) } shouldBe AttemptSaveOutcome.Saved
            subject.found(FIRST) shouldBe AttemptStory(grown)
        }

        "saving what is already kept changes nothing and still says so" {
            val subject = fresh()
            val attempt = Attempt(Purpose.Login, START).verifying(FactorKind.Google, secondsIn = 10)

            subject.inOwnTransaction { save(FIRST, attempt) }

            subject.inOwnTransaction { save(FIRST, attempt) } shouldBe AttemptSaveOutcome.Saved
            subject.found(FIRST) shouldBe AttemptStory(attempt)
        }

        "keeps instants to the microsecond, so an attempt saved twice is not taken for two histories" {
            val subject = fresh()
            val fineGrained = Instant.fromEpochSeconds(START.epochSeconds, 123_456_789)
            val attempt = Attempt(Purpose.Login, fineGrained).withFailure(fineGrained)

            subject.inOwnTransaction { save(FIRST, attempt) } shouldBe AttemptSaveOutcome.Saved
            subject.inOwnTransaction { save(FIRST, attempt.failingAt(secondsIn = 5)) } shouldBe
                AttemptSaveOutcome.Saved
        }

        "keeps each attempt under its own id" {
            val subject = fresh()
            val login = Attempt(Purpose.Login, START).verifying(FactorKind.Google, secondsIn = 10)
            val stepUp = Attempt(Purpose.StepUp, START + 60.seconds).failingAt(secondsIn = 70)

            subject.inOwnTransaction { save(FIRST, login) }
            subject.inOwnTransaction { save(SECOND, stepUp) }

            subject.found(FIRST) shouldBe AttemptStory(login)
            subject.found(SECOND) shouldBe AttemptStory(stepUp)
        }

        "refuses to forget a wrong answer another request recorded first" {
            val subject = fresh()
            val loaded = Attempt(Purpose.Login, START).verifying(FactorKind.Google, secondsIn = 10)
            subject.inOwnTransaction { save(FIRST, loaded) }
            val firstGuess = loaded.failingAt(secondsIn = 20)
            val secondGuess = loaded.failingAt(secondsIn = 21)

            subject.inOwnTransaction { save(FIRST, firstGuess) } shouldBe AttemptSaveOutcome.Saved
            subject.inOwnTransaction { save(FIRST, secondGuess) } shouldBe AttemptSaveOutcome.Superseded

            subject.found(FIRST) shouldBe AttemptStory(firstGuess)
        }

        "refuses to forget a factor another request verified first" {
            val subject = fresh()
            val loaded = Attempt(Purpose.Login, START)
            subject.inOwnTransaction { save(FIRST, loaded) }
            val withGoogle = loaded.verifying(FactorKind.Google, secondsIn = 10)
            val withTotp = loaded.verifying(FactorKind.Totp, secondsIn = 11)

            subject.inOwnTransaction { save(FIRST, withGoogle) } shouldBe AttemptSaveOutcome.Saved
            subject.inOwnTransaction { save(FIRST, withTotp) } shouldBe AttemptSaveOutcome.Superseded

            subject.found(FIRST) shouldBe AttemptStory(withGoogle)
        }

        "refuses an attempt that holds less than what is kept" {
            val subject = fresh()
            val began = Attempt(Purpose.Login, START)
            val grown = began.verifying(FactorKind.Google, secondsIn = 10).failingAt(secondsIn = 12)
            subject.inOwnTransaction { save(FIRST, grown) }

            subject.inOwnTransaction { save(FIRST, began) } shouldBe AttemptSaveOutcome.Superseded

            subject.found(FIRST) shouldBe AttemptStory(grown)
        }

        "refuses an attempt that began otherwise than the one kept under the same id" {
            val subject = fresh()
            val kept = Attempt(Purpose.Login, START)
            subject.inOwnTransaction { save(FIRST, kept) }

            subject.inOwnTransaction { save(FIRST, Attempt(Purpose.StepUp, START)) } shouldBe
                AttemptSaveOutcome.Superseded
            subject.inOwnTransaction { save(FIRST, Attempt(Purpose.Login, START + 1.seconds)) } shouldBe
                AttemptSaveOutcome.Superseded

            subject.found(FIRST) shouldBe AttemptStory(kept)
        }

        "a restored attempt can be saved again as it is" {
            val subject = fresh()
            val attempt = Attempt(Purpose.Login, START).verifying(FactorKind.Google, secondsIn = 10)
            subject.inOwnTransaction { save(FIRST, attempt) }
            val restored = checkNotNull(subject.inOwnTransaction { find(FIRST) })

            subject.inOwnTransaction { save(FIRST, restored.failingAt(secondsIn = 15)) } shouldBe
                AttemptSaveOutcome.Saved
        }
    }
}
