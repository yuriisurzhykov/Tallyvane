package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.LoginAttempts
import tallyvane.platform.cache.Counter
import kotlin.time.Duration

/**
 * [LoginAttempts] over `platform:cache`'s [Counter] — the one file in this module allowed to
 * import [Counter], since `identity:application` may not (`modules.yaml`; see [LoginAttempts]'s
 * own KDoc for why the port exists at all).
 *
 * A thin, faithful relay: whatever [Counter] does — including throwing when its store is
 * unavailable — reaches the caller unchanged. Deciding what a failure *means* is
 * [tallyvane.identity.application.SignInWithPasswordUseCase.RateLimited]'s job, not this
 * adapter's; a port should report, not already have picked the policy for whoever reads it.
 */
internal class LoginAttemptsOverCounter(private val counter: Counter) : LoginAttempts {
    override suspend fun failuresWithin(key: String, window: Duration): Long = counter.count(key, window)

    override suspend fun recordFailure(key: String, window: Duration) {
        counter.increment(key, window)
    }
}
