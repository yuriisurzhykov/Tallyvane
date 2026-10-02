package tallyvane.platform.idempotency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import tallyvane.platform.kernel.Fallback
import kotlin.time.Duration

/**
 * Deletes the claims whose day is over, every [every], for as long as the scope it runs in lives.
 *
 * The ledger already ignores an expired claim and lets the next request with its key take it over
 * (ADR-086), so a late sweep costs space and nothing else. That is why a failed sweep is only logged:
 * the next one, an hour later, does the same work, and nothing waits on this.
 *
 * The first sweep comes after [every] and not at start: a process that restarts often would otherwise
 * sweep at every start and a database that is not up yet would be asked at the worst moment.
 *
 * The process starts it (in its composition root, in a scope of its own that it cancels on the way
 * out) and nothing else asks for it.
 */
public class ClaimsSweep(private val ledger: Ledger, private val every: Duration) {
    /**
     * Starts sweeping in [scope]; cancelling the scope, or the returned job, stops it.
     */
    public fun startIn(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(every)
            sweep()
        }
    }

    private suspend fun sweep() {
        Fallback {
            val forgotten = ledger.forgetExpired()
            if (forgotten > 0) {
                logger.info("Forgot {} expired idempotency claims", forgotten)
            }
            forgotten
        }.orRecover { failure ->
            logger.warn("Expired idempotency claims could not be forgotten; the next sweep tries again", failure)
            0
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ClaimsSweep::class.java)
    }
}
