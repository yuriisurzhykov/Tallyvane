package tallyvane.platform.idempotency

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A [Ledger] and [Claims] that keep books instead of a table.
 *
 * What makes it more than a map is that a claim survives only if the transaction that took it
 * committed. It asks the [transactions] fake how each transaction ended instead of keeping a second
 * opinion, so a rolled-back claim disappears here exactly as it does in Postgres, and the conformance
 * suite's rollback cases are not passing for no reason.
 *
 * Single-threaded, like [TransactionRunnerFake] it leans on, so two copies in flight at once are for the
 * Postgres adapter's own specs.
 */
class LedgerFake(
    private val transactions: TransactionRunnerFake,
    private val clock: Clock,
    private val lifetime: Duration = 24.hours,
) : Ledger,
    Claims {
    private val entries = mutableListOf<Entry>()

    override fun take(claim: Claim) {
        val facts = Facts.of(claim)
        val standing = entries.firstOrNull { it.stands(facts) }
        if (standing != null) {
            throw ClaimedElsewhere()
        }
        entries.removeAll { it.sameClaimAs(facts) }
        entries += Entry(facts, transactions.endings.size, clock.now() + lifetime)
    }

    override suspend fun earlier(claim: Claim): Earlier {
        val facts = Facts.of(claim)
        val found = entries.firstOrNull { it.stands(facts) && it.committed() } ?: return Earlier.None
        return found.earlierFor(facts)
    }

    override suspend fun record(claim: Claim, answer: Answer) {
        entries.firstOrNull { it.stands(Facts.of(claim)) && it.committed() }?.answerWith(answer)
    }

    override suspend fun withhold(claim: Claim) {
        entries.firstOrNull { it.stands(Facts.of(claim)) && it.committed() }?.withhold()
    }

    override suspend fun forgetExpired(): Int {
        val expired = entries.filter { it.expiredAt(clock.now()) }
        entries.removeAll(expired.toSet())
        return expired.count { it.committed() }
    }

    /**
     * Whether a transaction kept [this] entry: it ended, and committed.
     */
    private fun Entry.committed(): Boolean =
        transactions.endings.getOrNull(transaction) == TransactionRunnerFake.Ending.Committed

    /**
     * A claim is standing if it is not expired and its transaction neither rolled back nor is unknown:
     * one still open counts, which is what a second copy would run into.
     */
    private fun Entry.stands(other: Facts): Boolean = sameClaimAs(other) &&
        !expiredAt(clock.now()) &&
        transactions.endings.getOrNull(transaction) != TransactionRunnerFake.Ending.RolledBack

    private class Entry(private val facts: Facts, val transaction: Int, private val expires: Instant) {
        private var answer: Answer? = null
        private var withheld = false

        fun sameClaimAs(other: Facts): Boolean = facts.owner == other.owner && facts.key == other.key

        fun expiredAt(now: Instant): Boolean = expires <= now

        fun earlierFor(asked: Facts): Earlier = when {
            !facts.fingerprint.contentEquals(asked.fingerprint) -> Earlier.Different
            withheld -> Earlier.Withheld
            else -> answer?.let { kept -> Earlier.Replay(kept) } ?: Earlier.Unanswered
        }

        fun answerWith(given: Answer) {
            if (answer == null && !withheld) {
                answer = given
            }
        }

        fun withhold() {
            if (answer == null) {
                withheld = true
            }
        }
    }

    /**
     * A claim read the way storage reads it: by what it says to a record.
     */
    private class Facts(val owner: String, val key: Uuid, val fingerprint: ByteArray) {
        companion object {
            fun of(claim: Claim): Facts {
                val heard = Hearing()
                claim.writeTo(heard)
                return heard.facts()
            }
        }
    }

    private class Hearing : Claim.Record {
        private var owner: String? = null
        private var key: Uuid? = null
        private var fingerprint: ByteArray? = null

        override fun claimed(owner: String, key: Uuid, fingerprint: ByteArray) {
            this.owner = owner
            this.key = key
            this.fingerprint = fingerprint
        }

        fun facts(): Facts = Facts(checkNotNull(owner), checkNotNull(key), checkNotNull(fingerprint))
    }
}
