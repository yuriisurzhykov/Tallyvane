package tallyvane.platform.kernel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The behaviour every [TransactionRunner] must show, whatever it runs on.
 *
 * Written once and inherited by each implementation's spec: the fake here, and
 * the Exposed adapter in `platform:persistence` when it arrives. That is the
 * point — a fake tested separately from the adapter is free to disagree with it,
 * and the disagreement surfaces in production rather than in a build (ADR-046).
 *
 * Whether writes survived cannot be asked through the port, and should not be: a
 * `didYouCommit()` method would exist only for tests. So each implementation
 * supplies a [Subject] instead, pairing a runner with a way to watch what its
 * writes did.
 *
 * [fresh] returns a subject with no history, and every case here starts from one.
 * That requirement is part of this suite rather than a Kotest isolation setting,
 * because the next implementation has to satisfy it too — for Postgres "fresh"
 * will mean an empty table, which no framework flag arranges.
 */
abstract class TransactionRunnerConformance : StringSpec() {
    /**
     * A runner with no history, and a probe over its writes.
     */
    protected abstract suspend fun fresh(): Subject

    /**
     * A runner under test, paired with the means to observe its writes.
     */
    interface Subject {
        val transactions: TransactionRunner

        /**
         * Performs one write whose fate [survivingWrites] will report.
         */
        suspend fun write()

        /**
         * How many writes are still there, counting only committed ones.
         */
        suspend fun survivingWrites(): Int
    }

    init {
        "hands back the value a commit carried" {
            fresh().transactions.inTransaction { Verdict.Commit(42) } shouldBe 42
        }

        "hands back the value a rollback carried, so a refusal still answers" {
            fresh().transactions.inTransaction { Verdict.Rollback("rejected") } shouldBe "rejected"
        }

        "keeps writes that a commit covered" {
            val subject = fresh()

            subject.transactions.inTransaction {
                subject.write()
                Verdict.Commit(Unit)
            }

            subject.survivingWrites() shouldBe 1
        }

        "discards writes on rollback, although the block returned normally" {
            val subject = fresh()

            subject.transactions.inTransaction {
                subject.write()
                Verdict.Rollback(Unit)
            }

            subject.survivingWrites() shouldBe 0
        }

        "discards writes when the block throws, and lets the failure through" {
            val subject = fresh()

            shouldThrow<IllegalStateException> {
                subject.transactions.inTransaction {
                    subject.write()
                    error("boom")
                }
            }

            subject.survivingWrites() shouldBe 0
        }

        "discards writes when the coroutine is cancelled mid-transaction" {
            val subject = fresh()

            val job =
                launch {
                    subject.transactions.inTransaction {
                        subject.write()
                        delay(50.milliseconds)
                        Verdict.Commit(Unit)
                    }
                }
            job.cancel()
            job.join()

            subject.survivingWrites() shouldBe 0
        }

        "refuses a nested transaction rather than opening a second one" {
            val subject = fresh()

            shouldThrow<IllegalStateException> {
                subject.transactions.inTransaction {
                    subject.transactions.inTransaction { Verdict.Commit(Unit) }
                    Verdict.Commit(Unit)
                }
            }
        }
    }
}
