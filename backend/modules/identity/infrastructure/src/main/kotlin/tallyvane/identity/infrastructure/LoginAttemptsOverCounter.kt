package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.LoginAttempts
import tallyvane.platform.cache.Counter
import kotlin.time.Duration

/**
 * [LoginAttempts] over `platform:cache`'s [Counter] — throws whatever [Counter] throws, uncaught.
 *
 * Why this is the one file allowed to import [Counter], and why it never catches or logs:
 * `infrastructure/README.md`.
 */
internal class LoginAttemptsOverCounter(private val counter: Counter) : LoginAttempts {
    override suspend fun failuresWithin(key: String, window: Duration): Long = counter.count(key, window)

    override suspend fun recordFailure(key: String, window: Duration) {
        counter.increment(key, window)
    }
}
