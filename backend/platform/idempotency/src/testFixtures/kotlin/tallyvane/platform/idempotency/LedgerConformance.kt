package tallyvane.platform.idempotency

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.withContext
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The behaviour every [Ledger] and [Claims] pair must show, whatever it keeps its books in.
 *
 * Written once and inherited by each implementation's spec: the fake here, and the Postgres adapter in
 * `platform:persistence`. A fake tested apart from the adapter is free to disagree with it, and the
 * disagreement surfaces in production (ADR-046).
 *
 * Every case runs its work through [Subject.transactions], the runner that takes the claim, because
 * that is the only way a claim ever gets taken. Whether the work survived is asked of the subject
 * rather than of the port, for the reason `TransactionRunnerConformance` gives: a `didItCommit()` on the
 * port would exist only for tests.
 */
abstract class LedgerConformance : StringSpec() {
    /**
     * A ledger with no history, and the means to run work under a claim and to let time pass.
     */
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val ledger: Ledger

        /**
         * The runner that takes the claim in the context, first, in the transaction it opens.
         */
        val transactions: TransactionRunner

        /**
         * Performs one write whose fate [survivingWork] reports.
         */
        suspend fun work()

        /**
         * How many writes of [work] are still there, counting only committed ones.
         */
        suspend fun survivingWork(): Int

        /**
         * Lets [by] pass for the ledger's clock.
         */
        fun later(by: Duration)
    }

    private suspend fun Subject.commits(claim: Claim) {
        withContext(claim) {
            transactions.inTransaction {
                work()
                Verdict.Commit(Unit)
            }
        }
    }

    init {
        "knows nothing of a claim nobody took" {
            fresh().ledger.earlier(claimed(1)) shouldBe Earlier.None
        }

        "knows a claim committed with its work, with no answer yet" {
            val subject = fresh()

            subject.commits(claimed(1))

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.Unanswered
            subject.survivingWork() shouldBe 1
        }

        "gives back the answer that was kept, byte for byte" {
            val subject = fresh()
            subject.commits(claimed(1))

            subject.ledger.record(claimed(1), answer(201, "application/json", """{"id":"7"}"""))

            heard(subject.ledger.earlier(claimed(1))) shouldBe Heard(201, "application/json", """{"id":"7"}""")
        }

        "keeps the first answer and ignores a second" {
            val subject = fresh()
            subject.commits(claimed(1))

            subject.ledger.record(claimed(1), answer(201, "application/json", "first"))
            subject.ledger.record(claimed(1), answer(200, "application/json", "second"))

            heard(subject.ledger.earlier(claimed(1))).body shouldBe "first"
        }

        "remembers an answer that was withheld, and gives it up to no one" {
            val subject = fresh()
            subject.commits(claimed(1))

            subject.ledger.withhold(claimed(1))
            subject.ledger.record(claimed(1), answer(200, "application/json", "late"))

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.Withheld
        }

        "forgets a claim whose work rolled back, although the block returned normally" {
            val subject = fresh()

            withContext(claimed(1)) {
                subject.transactions.inTransaction {
                    subject.work()
                    Verdict.Rollback(Unit)
                }
            }

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.None
            subject.survivingWork() shouldBe 0
        }

        "forgets a claim whose work threw, and lets the failure through" {
            val subject = fresh()

            shouldThrow<IllegalStateException> {
                withContext(claimed(1)) {
                    subject.transactions.inTransaction {
                        subject.work()
                        error("boom")
                    }
                }
            }

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.None
            subject.survivingWork() shouldBe 0
        }

        "does not answer a repeat whose work rolled back, so the repeat runs" {
            val subject = fresh()
            withContext(claimed(1)) {
                subject.transactions.inTransaction { Verdict.Rollback(Unit) }
            }

            subject.ledger.record(claimed(1), answer(422, "application/json", "refused"))

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.None
        }

        "lets a repeat run after the first rolled back" {
            val subject = fresh()
            withContext(claimed(1)) {
                subject.transactions.inTransaction {
                    subject.work()
                    Verdict.Rollback(Unit)
                }
            }

            subject.commits(claimed(1))

            subject.survivingWork() shouldBe 1
        }

        "refuses a second request with the same key once the first committed, and runs none of its work" {
            val subject = fresh()
            subject.commits(claimed(1))

            shouldThrow<ClaimedElsewhere> { subject.commits(claimed(1)) }

            subject.survivingWork() shouldBe 1
        }

        "tells a key reused for another request from a repeat" {
            val subject = fresh()
            subject.commits(claimed(1, body = "a"))

            subject.ledger.earlier(claimed(1, body = "b")) shouldBe Earlier.Different
        }

        "keeps one owner's key from another" {
            val subject = fresh()
            subject.commits(claimed(1, owner = Owner.anonymous()))

            subject.ledger.earlier(claimed(1, owner = Owner.subject(PERSON))) shouldBe Earlier.None
            subject.commits(claimed(1, owner = Owner.subject(PERSON)))
            subject.survivingWork() shouldBe 2
        }

        "lets two different keys of one owner both run" {
            val subject = fresh()

            subject.commits(claimed(1))
            subject.commits(claimed(2))

            subject.survivingWork() shouldBe 2
        }

        "treats a claim as free once its day is over, and runs the repeat" {
            val subject = fresh()
            subject.commits(claimed(1))

            subject.later(25.hours)

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.None
            subject.commits(claimed(1))
            subject.survivingWork() shouldBe 2
        }

        "keeps the late answer of a request off the claim of another request that took the key over" {
            val subject = fresh()
            val first = claimed(1, body = "first")
            subject.commits(first)
            subject.later(25.hours)
            val second = claimed(1, body = "second")
            subject.commits(second)

            subject.ledger.record(first, answer(201, "application/json", """{"first":true}"""))
            subject.ledger.withhold(first)

            subject.ledger.earlier(second) shouldBe Earlier.Unanswered
        }

        "still holds a claim a little before its day is over" {
            val subject = fresh()
            subject.commits(claimed(1))

            subject.later(24.hours - 1.minutes)

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.Unanswered
        }

        "forgets only the claims whose day is over, and says how many" {
            val subject = fresh()
            subject.commits(claimed(1))
            subject.commits(claimed(2))
            subject.later(23.hours)
            subject.commits(claimed(3))
            subject.later(2.hours)

            subject.ledger.forgetExpired() shouldBe 2

            subject.ledger.earlier(claimed(1)) shouldBe Earlier.None
            subject.ledger.earlier(claimed(3)) shouldBe Earlier.Unanswered
        }
    }

    private data class Heard(val status: Int, val contentType: String?, val body: String)

    private fun heard(earlier: Earlier): Heard {
        earlier.shouldBeInstanceOf<Earlier.Replay>()
        var heard: Heard? = null
        earlier.tell(
            object : Answer.Record {
                override fun answered(status: Int, contentType: String?, body: ByteArray) {
                    heard = Heard(status, contentType, body.decodeToString())
                }
            },
        )
        return checkNotNull(heard)
    }

    private fun answer(status: Int, contentType: String, body: String): Answer =
        Answer(status, contentType, body.encodeToByteArray())

    private companion object {
        val PERSON = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-0000000000aa")
    }
}

/**
 * A claim with the key `…-00000000000n`, owner and body as given: the same arguments are the same claim.
 */
fun claimed(n: Int, owner: Owner = Owner.anonymous(), body: String = "body"): Claim = Claim(
    owner,
    checkNotNull(IdempotencyKey.parse("00000000-0000-0000-0000-" + n.toString().padStart(12, '0'))),
    Fingerprint.of("POST", "/api/v1/notes", body.encodeToByteArray()),
)
