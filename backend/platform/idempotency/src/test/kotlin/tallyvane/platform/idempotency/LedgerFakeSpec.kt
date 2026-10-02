package tallyvane.platform.idempotency

import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The fake, judged by the same suite as the Postgres adapter.
 */
class LedgerFakeSpec : LedgerConformance() {
    override suspend fun fresh(): Subject {
        val runner = TransactionRunnerFake()
        val clock = ClockSteppable(Instant.parse("2026-10-01T12:00:00Z"))
        val ledger = LedgerFake(runner, clock)
        return object : Subject {
            override val ledger: Ledger = ledger
            override val transactions = ClaimedTransactions(runner, ledger)

            override suspend fun work() = runner.write()

            override suspend fun survivingWork(): Int = runner.survivingWrites()

            override fun later(by: Duration) = clock.advance(by)
        }
    }
}
