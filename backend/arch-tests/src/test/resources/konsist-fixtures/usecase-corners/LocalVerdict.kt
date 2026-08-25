package tallyvane.platform.persistence

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

// Allowed: a local of that type is how an implementation of the port holds the
// block's answer before deciding. `no-verdict-in-signature` is about declared
// signatures, and this asserts the local is not mistaken for one.
internal class ExposedTransactions : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T {
        var verdict: Verdict<T>? = null
        try {
            verdict = block()
            return verdict.value
        } finally {
            check(verdict != null || true)
        }
    }
}
