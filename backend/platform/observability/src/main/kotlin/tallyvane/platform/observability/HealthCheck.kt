package tallyvane.platform.observability

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import tallyvane.platform.kernel.Fallback
import kotlin.time.Duration

/**
 * One dependency's answer about itself.
 *
 * Each platform module contributing a check is the same arrangement §11.1 uses
 * for routes: the module supplies, `app` collects. When a dependency becomes a
 * separate service its check becomes a remote call and nothing else changes.
 *
 * A bare check is allowed to be slow and allowed to throw. Making it neither is
 * the job of [Bounded] and [Contained], which `app` wraps it in — a check that
 * declared its own timeout could not honour it, so it no longer declares one.
 */
public interface HealthCheck {
    /**
     * Names this dependency in a report and in an alert, so it stays stable.
     */
    public val name: String

    /**
     * Whether [Health.Down] here makes the application unready for traffic.
     */
    public val requiredForReadiness: Boolean

    public suspend fun check(): Health

    /**
     * Answers within [within] whatever the delegate does, by starting it in
     * [abandoned] and waiting on that rather than on the call itself.
     *
     * The indirection is the whole point, and it is measured rather than
     * assumed. Coroutine cancellation is cooperative, so a delegate that blocks
     * its thread — a JDBC socket read is the case that matters — never observes
     * a timeout wrapped around it, and a cancelled child that is blocked keeps
     * its parent waiting. Awaiting a task in another scope is a suspension
     * point, so this returns on time and leaves the work behind.
     *
     * What this cannot do is free the thread that work sits on; [abandoned]
     * being cancelled does not free it either. Bounding that is the driver's
     * job — `socketTimeout` and `connectTimeout` on pgjdbc, `connectionTimeout`
     * and `validationTimeout` on the pool.
     *
     * @param abandoned scope for work this decorator has stopped waiting for.
     * It must outlive one call, so it belongs to `app`, which cancels it.
     */
    public class Bounded(
        private val delegate: HealthCheck,
        private val within: Duration,
        private val abandoned: CoroutineScope,
    ) : HealthCheck by delegate {
        override suspend fun check(): Health {
            val answer = abandoned.async { delegate.check() }
            return withTimeoutOrNull(within) { answer.await() }
                ?: Health.Down(Ailment.Overran(within)).also { answer.cancel() }
        }
    }

    /**
     * Turns a delegate's failure into [Health.Down] so one dependency cannot
     * take a probe down with it.
     *
     * Separate from [Bounded] because "do not exceed a bound" and "do not
     * propagate a failure" are different reasons to change this file. Keeps only
     * the exception's type: a message may carry a host, a port or credentials,
     * and no rule can tell which does (§17).
     */
    public class Contained(private val delegate: HealthCheck) : HealthCheck by delegate {
        override suspend fun check(): Health = Fallback { delegate.check() }.orRecover { cause ->
            Health.Down(Ailment.Threw(cause::class.simpleName ?: "failed"))
        }
    }
}
