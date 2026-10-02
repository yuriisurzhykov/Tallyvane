package tallyvane.platform.idempotency

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.withContext
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.platform.kernel.Verdict

/**
 * What the decorator adds to a transaction, apart from what a ledger does with the claim.
 */
class ClaimedTransactionsSpec :
    StringSpec(
        {
            "takes the claim before the work runs, in the same transaction" {
                val events = mutableListOf<String>()
                val runner = TransactionRunnerFake()
                val claims = object : Claims {
                    override fun take(claim: Claim) {
                        events += "claim taken, open=${runner.endings.size}"
                    }
                }

                withContext(claimed(1)) {
                    ClaimedTransactions(runner, claims).inTransaction {
                        events += "work"
                        Verdict.Commit(Unit)
                    }
                }

                events shouldBe listOf("claim taken, open=0", "work")
            }

            "passes a call with no claim straight through, taking nothing" {
                val runner = TransactionRunnerFake()
                val claims = object : Claims {
                    override fun take(claim: Claim) = error("nothing was claimed")
                }

                ClaimedTransactions(runner, claims).inTransaction {
                    runner.write()
                    Verdict.Commit("kept")
                } shouldBe "kept"

                runner.survivingWrites() shouldBe 1
            }

            "hands back the value of a rollback, as the runner underneath does" {
                val runner = TransactionRunnerFake()
                val claims = LedgerFake(runner, ClockSteppable(Instant0))

                withContext(claimed(1)) {
                    ClaimedTransactions(runner, claims).inTransaction { Verdict.Rollback("refused") }
                } shouldBe "refused"
            }

            "refuses a second transaction once the first committed under the claim" {
                val runner = TransactionRunnerFake()
                val transactions = ClaimedTransactions(runner, LedgerFake(runner, ClockSteppable(Instant0)))

                val failure = shouldThrow<IllegalStateException> {
                    withContext(claimed(1)) {
                        transactions.inTransaction { Verdict.Commit(Unit) }
                        transactions.inTransaction { Verdict.Commit(Unit) }
                    }
                }

                failure.message shouldContain "One request is one use case is one transaction"
            }

            "lets a second transaction take the claim again when the first rolled back" {
                val runner = TransactionRunnerFake()
                val transactions = ClaimedTransactions(runner, LedgerFake(runner, ClockSteppable(Instant0)))

                withContext(claimed(1)) {
                    transactions.inTransaction { Verdict.Rollback(Unit) }
                    transactions.inTransaction {
                        runner.write()
                        Verdict.Commit(Unit)
                    }
                }

                runner.survivingWrites() shouldBe 1
            }

            "lets a second transaction take the claim again when the first threw" {
                val runner = TransactionRunnerFake()
                val transactions = ClaimedTransactions(runner, LedgerFake(runner, ClockSteppable(Instant0)))

                withContext(claimed(1)) {
                    shouldThrow<IllegalStateException> { transactions.inTransaction { error("boom") } }
                    transactions.inTransaction {
                        runner.write()
                        Verdict.Commit(Unit)
                    }
                }

                runner.survivingWrites() shouldBe 1
            }
        },
    )

private val Instant0 = kotlin.time.Instant.parse("2026-10-01T12:00:00Z")
