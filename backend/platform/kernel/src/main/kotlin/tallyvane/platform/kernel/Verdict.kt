package tallyvane.platform.kernel

/**
 * What a transactional block tells the transaction to do with its writes.
 *
 * A block cannot return without naming one of the two branches, which is the
 * whole reason this type exists. The mistake it forecloses is specific: a use
 * case decides inside a transaction that the request must be refused, returns
 * that refusal as an ordinary value, and the transaction — having seen a normal
 * return — commits everything written before the refusal. Nothing in a signature
 * would have objected, and the row would simply be there afterwards.
 *
 * ```
 * val outcome = transactions.inTransaction {
 *     when (users.insert(candidate)) {
 *         InsertOutcome.AlreadyExists -> Verdict.Rollback(SignInOutcome.Rejected("email taken"))
 *         InsertOutcome.Inserted -> Verdict.Commit(SignInOutcome.Succeeded(sessions.open(candidate.id)))
 *     }
 * }
 * ```
 *
 * [TransactionRunner.inTransaction] returns [value] either way, so a rolled-back
 * block still answers its caller — it simply answers without having changed
 * anything.
 *
 * ### This is a directive, not a result
 *
 * `Verdict` never crosses a layer boundary. It is not returned from a use case,
 * never appears in a module's `contract`, and is never a port's return type; it
 * is produced as the last expression of a block and consumed in the same call.
 * That distinction is what keeps it from becoming the second competing result
 * type ENGINEERING-PRINCIPLES.md rejects, and `no-verdict-in-signature` makes it
 * a checked fact rather than an intention.
 *
 * ### The hazard it introduces
 *
 * [Rollback] discards *everything* the block wrote, including a write meant to
 * outlive the refusal — a failed-attempt record for rate limiting is the usual
 * example. Such a write belongs outside the transaction. No rule catches this,
 * because telling a write that must survive from one that must not requires
 * knowing what it means; see ADR-052.
 */
public sealed interface Verdict<out T> {
    /**
     * The value [TransactionRunner.inTransaction] hands back, committed or not.
     */
    public val value: T

    /**
     * Keep everything the block wrote.
     */
    public data class Commit<out T>(override val value: T) : Verdict<T>

    /**
     * Discard everything the block wrote, and still answer with [value].*/
    public data class Rollback<out T>(override val value: T) : Verdict<T>
}
