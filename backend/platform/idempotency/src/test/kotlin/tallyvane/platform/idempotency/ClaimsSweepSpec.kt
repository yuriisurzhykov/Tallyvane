package tallyvane.platform.idempotency

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.TransactionRunnerFake
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val SOON = 10.milliseconds

private val LATER = 1.hours

private val DEADLINE = 5.seconds

/**
 * A ledger that counts how often it was asked to forget, and fails the first [failing] times.
 */
private class Sweepable(private val failing: Int = 0) :
    Ledger by LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-01T12:00:00Z"))) {
    val asked = AtomicInteger()

    override suspend fun forgetExpired(): Int {
        check(asked.incrementAndGet() > failing) { "The database is not there" }
        return 0
    }
}

private suspend fun Sweepable.askedAtLeast(times: Int) {
    withTimeout(DEADLINE) {
        while (asked.get() < times) {
            delay(SOON)
        }
    }
}

private fun background(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class ClaimsSweepSpec :
    StringSpec(
        {
            "forgets again and again for as long as it lives" {
                val ledger = Sweepable()
                val scope = background()

                ClaimsSweep(ledger, SOON).startIn(scope)

                ledger.askedAtLeast(3)
                scope.cancel()
            }

            "does not forget before the first interval has passed" {
                val ledger = Sweepable()
                val scope = background()

                ClaimsSweep(ledger, LATER).startIn(scope)
                delay(SOON * 10)

                ledger.asked.get() shouldBe 0
                scope.cancel()
            }

            "goes on after a sweep failed" {
                val ledger = Sweepable(failing = 2)
                val scope = background()

                ClaimsSweep(ledger, SOON).startIn(scope)

                ledger.askedAtLeast(4)
                scope.cancel()
            }

            "stops when its scope is cancelled" {
                val ledger = Sweepable()
                val scope = background()
                ClaimsSweep(ledger, SOON).startIn(scope)
                ledger.askedAtLeast(1)

                scope.cancel()
                delay(SOON * 5)
                val settled = ledger.asked.get()
                delay(SOON * 10)

                ledger.asked.get() shouldBe settled
            }
        },
    )
