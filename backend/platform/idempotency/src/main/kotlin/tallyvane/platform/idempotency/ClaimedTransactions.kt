package tallyvane.platform.idempotency

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.coroutines.coroutineContext

/**
 * The [TransactionRunner] every use case receives: [origin], and the request's [Claim] taken first.
 *
 * A request with a claim in its context gets it taken as the first statement of its transaction, so
 * the claim and the work commit or roll back together by the database's own guarantee. A call with none
 * (a health check, a read, a sweep started by the process itself) passes through untouched.
 *
 * Decorating the runner, rather than asking each use case, is what makes the guarantee unforgettable:
 * the only runner a module can obtain is this one (ADR-086).
 */
public class ClaimedTransactions(private val origin: TransactionRunner, private val claims: Claims) :
    TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T {
        val claim = coroutineContext[Claim] ?: return origin.inTransaction(block)
        return claim.around(origin, claims, block)
    }
}
