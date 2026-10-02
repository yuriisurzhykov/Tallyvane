package tallyvane.platform.idempotency

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

/**
 * One request's claim to be the only one of its kind: its owner, the key it came with, and what it
 * amounted to.
 *
 * It rides in the coroutine context, as the trace does, and nothing a use case writes ever mentions
 * it. The transaction runner every use case receives takes it as the first statement of the
 * transaction and keeps it for as long as the work does (ADR-086), so the claim commits with the work
 * or rolls back with it, and a use case cannot forget either.
 *
 * ```
 * withContext(Claim(owner, key, fingerprint)) { route.handle(call) }
 * ```
 *
 * It says what it holds only to a [Record], like the stored aggregates of ADR-085: storage needs the
 * three facts, and a getter for each would let anything decide from them.
 */
public class Claim(
    private val owner: Owner,
    private val idempotencyKey: IdempotencyKey,
    private val fingerprint: Fingerprint,
) : AbstractCoroutineContextElement(Key) {
    private val committed = AtomicBoolean(false)

    /**
     * Says the three facts to [record], once.
     */
    public fun writeTo(record: Record) {
        record.claimed(owner.text(), idempotencyKey.uuid(), fingerprint.bytes())
    }

    /**
     * Runs [block] in a transaction of [transactions] that [claims] takes this claim in first.
     *
     * A transaction that did not commit (the block threw, or said [Verdict.Rollback]) leaves this
     * claim free to be taken again, so a retry the use case wrote for itself works. A transaction that
     * committed uses it up, and a second one afterwards is refused: its writes would sit outside the
     * claim, so a crash between the two would leave a repeat seeing "done" for work that was half done.
     * It is ADR-052's rule against nesting, applied in sequence.
     */
    internal suspend fun <T> around(
        transactions: TransactionRunner,
        claims: Claims,
        block: suspend () -> Verdict<T>,
    ): T {
        check(!committed.get()) {
            "This request already committed a transaction under its Idempotency-Key. A second one " +
                "would write outside the claim, so a repeat after a crash between them would see " +
                "'done' for work that is half done. One request is one use case is one transaction (ADR-052)."
        }
        var ending: Verdict<T>? = null
        val value = transactions.inTransaction {
            claims.take(this)
            block().also { verdict -> ending = verdict }
        }
        if (ending is Verdict.Commit) {
            committed.set(true)
        }
        return value
    }

    override fun toString(): String = "Claim($owner, $fingerprint)"

    /**
     * Whoever listens to a [Claim]: storage turns it into a row.
     */
    public interface Record {
        public fun claimed(owner: String, key: Uuid, fingerprint: ByteArray)
    }

    public companion object Key : CoroutineContext.Key<Claim>
}
