package tallyvane.identity.application.port

import kotlin.time.Duration

/**
 * A [LoginAttempts] backed by a plain in-memory count, with no window expiry — a use case's own
 * test cares whether a failure was recorded and counted, not whether a window closes, which
 * `platform:cache`'s own `CounterInMemorySpec` already covers.
 */
internal class LoginAttemptsFake : LoginAttempts {
    private val counts = mutableMapOf<String, Long>()

    override suspend fun failuresWithin(key: String, window: Duration): Long = counts[key] ?: 0

    override suspend fun recordFailure(key: String, window: Duration) {
        counts[key] = (counts[key] ?: 0) + 1
    }
}
