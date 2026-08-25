package tallyvane.platform.kernel

/**
 * One database transaction as a collaborator: all of these writes, or none.
 *
 * Completing a sign-in inserts a user, grants a capability and opens a session.
 * Without a transaction a failure on the third leaves a user with no session and
 * half its rights — rubbish somebody cleans up by hand later. Inside one, the
 * three either all happened or none did.
 *
 * The port exists because §4.3 puts the transaction boundary in the use case,
 * and a use case lives in `application`, which `modules.yaml` allows to depend on
 * `platform:kernel` and not on `platform:persistence`. So the code that decides
 * where a transaction begins cannot name Exposed, JDBC or a connection — it names
 * this. A test of that use case substitutes a fake and needs no database at all.
 *
 * ### The block says how it ends
 *
 * The block returns a [Verdict], so committing is something it states rather than
 * something that happens because the block returned. See [Verdict] for the
 * mistake this forecloses and for the one hazard it introduces.
 *
 * ### Nesting is refused, loudly
 *
 * Calling `inTransaction` inside `inTransaction` throws. Silently opening a second
 * transaction would be the worse outcome: part of the work could commit while the
 * rest rolled back, which is precisely the state this port exists to prevent. When
 * two writes must be atomic, they belong to one use case, not two — the same
 * shape §11.1 and `web-one-usecase` already require of a route.
 *
 * A consequence worth knowing before it bites: a method a neighbour calls through
 * its `contract` must not open a transaction, or a caller that wrapped it will
 * fail here for reasons it never suspected. Reads do not need one, so the
 * restriction is cheap (ADR-052).
 *
 * ### Cancellation rolls back
 *
 * If the coroutine is cancelled part-way, the block never produces a `Verdict` and
 * the writes are discarded. An implementation must not treat a cancelled block as
 * a committed one.
 *
 * Isolation levels, read-only transactions and savepoints are deliberately absent
 * until a real caller needs one.
 */
public interface TransactionRunner {
    /**
     * Runs [block] in one transaction and returns the value its [Verdict] carries.
     *
     * @throws IllegalStateException if a transaction is already open on this
     * runner, which is a design error rather than a runtime condition.
     */
    public suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T
}
