package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.AccountFailures
import tallyvane.authentication.domain.AccountGuesses
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-03T09:00:00Z")
private val FIRST = 1.seconds

/**
 * The behaviour every [AccountFailures] must show, whatever keeps them.
 *
 * What comes back is judged by the pause it earns, which is the only thing anybody asks of it.
 */
abstract class AccountFailuresConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val failures: AccountFailures
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: () -> T): T =
        transactions.inTransaction { Verdict.Commit(call()) }

    private suspend fun Subject.pauseOf(account: AccountId, at: Instant): Duration =
        inOwnTransaction { failures.recent(account, at - AccountGuesses.WINDOW).pauseLeft(at, FIRST) }

    init {
        "an account nobody got wrong has nothing to wait for" {
            val subject = fresh()

            subject.pauseOf(ANN, NOW) shouldBe Duration.ZERO
        }

        "a wrong code earns the first delay, and each further one doubles it" {
            val subject = fresh()

            subject.inOwnTransaction { subject.failures.record(ANN, NOW) }
            subject.pauseOf(ANN, NOW) shouldBe 1.seconds

            subject.inOwnTransaction { subject.failures.record(ANN, NOW) }
            subject.pauseOf(ANN, NOW) shouldBe 2.seconds
        }

        "each account has its own count" {
            val subject = fresh()
            subject.inOwnTransaction { subject.failures.record(ANN, NOW) }

            subject.pauseOf(BOB, NOW) shouldBe Duration.ZERO
        }

        "a wrong code from before the window asked for is not returned" {
            val subject = fresh()
            subject.inOwnTransaction { subject.failures.record(ANN, NOW - 20.minutes) }

            subject.pauseOf(ANN, NOW) shouldBe Duration.ZERO
        }
    }
}
