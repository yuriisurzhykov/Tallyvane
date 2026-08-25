package tallyvane.platform.persistence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * [TransactionRunner] over Exposed and a JDBC connection.
 *
 * ### `suspend` here does not mean non-blocking
 *
 * `suspendTransaction` from `exposed-jdbc` exists, in JetBrains' own words, "for
 * compatibility with JDBC drivers, to call suspend functions alongside blocking
 * database operations". The SQL underneath still blocks its thread, because JDBC's
 * interface has no way not to: it returns a result, and a result cannot be returned
 * before it arrives. So the signature means "callable from a coroutine", not "costs
 * no thread" — and the [dispatcher] is what keeps that honest.
 *
 * Blocking work runs only on [dispatcher], whose parallelism equals the connection
 * pool's size. Without that bound, a burst of transactions would wait inside
 * `getConnection` on shared IO threads and starve unrelated work of them — the same
 * failure `HealthCheck.Bounded` was written to prevent, one layer down.
 *
 * ### Nesting fails rather than joining
 *
 * Exposed's default is to let a nested block share the outer transaction, so an
 * inner rollback silently discards the outer's writes too. Measured, not assumed: a
 * nested `suspendTransaction` reports the same transaction id as its parent. The
 * port forbids nesting, so this checks for an open transaction first and refuses.
 * `TransactionManager.currentOrNull()` is a sound detector even on a multi-threaded
 * dispatcher — the same probe showed the transaction survives a `withContext` hop,
 * because Exposed 1.x carries it in the coroutine context rather than a thread-local.
 */
internal class ExposedTransactionRunner(private val database: Database, private val dispatcher: CoroutineDispatcher) :
    TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T {
        check(TransactionManager.currentOrNull() == null) {
            "A transaction is already open. Two writes atomic enough to share one belong to one use case, not two."
        }
        return withContext(dispatcher) {
            suspendTransaction(db = database) {
                val verdict = block()
                if (verdict is Verdict.Rollback) {
                    rollback()
                }
                verdict.value
            }
        }
    }
}
