package tallyvane.platform.kernel

/**
 * A [TransactionRunner] that keeps books instead of a database.
 *
 * [write] stands in for a write, and [survivingWrites] reports only the ones a
 * committed transaction kept — so the fake *simulates* rollback rather than
 * merely recording that one happened. Without that, the conformance suite's
 * rollback cases would pass here for no reason and the fake would be free to
 * drift from the real adapter, which is the drift ADR-046 exists to catch.
 *
 * [endings] lets a use-case test assert that a refusal rolled back, with no
 * database in sight.
 *
 * Lives in `src/testFixtures` rather than `src/main`, so it never ships in the
 * production jar (ADR-044), and rather than `src/test`, so a feature module's
 * use-case tests can substitute it.
 */
class TransactionRunnerFake : TransactionRunner {
    private val recorded = mutableListOf<Ending>()
    private var open = false
    private var committed = 0
    private var pending = 0

    /**
     * How each transaction ended, in the order they were opened.
     */
    val endings: List<Ending> get() = recorded.toList()

    /**
     * Stands in for one write inside the current transaction.
     */
    fun write() {
        pending += 1
    }

    /**
     * Writes that a committed transaction kept.
     */
    fun survivingWrites(): Int = committed

    override suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T {
        check(!open) {
            "A transaction is already open. Nesting is a design error: when two writes must be " +
                "atomic they belong to one use case, not two (ADR-052)."
        }
        open = true
        // Held rather than caught: a `catch` here would have to name `Throwable` to
        // see cancellation, and `finally` reaches the same conclusion without one.
        var verdict: Verdict<T>? = null
        try {
            verdict = block()
            return verdict.value
        } finally {
            recorded += ending(verdict)
            pending = 0
            open = false
        }
    }

    private fun ending(verdict: Verdict<*>?): Ending = when (verdict) {
        is Verdict.Commit -> {
            committed += pending
            Ending.Committed
        }

        // Null means the block threw or was cancelled, so it never said anything.
        else -> Ending.RolledBack
    }

    enum class Ending { Committed, RolledBack }
}
