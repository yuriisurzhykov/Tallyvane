package tallyvane.identity.application.port

import kotlin.time.Duration

/**
 * How many recent failures identity's own rate limiting has recorded against one key — a sign-in
 * attempt by email today, a second-factor verification attempt by pending id later.
 *
 * Why this port exists instead of `application` depending on `platform:cache` directly:
 * `application/README.md`.
 */
public interface LoginAttempts {
    /**
     * Failures recorded against [key] within the trailing [window] ending now.
     *
     * @param key Identifies what is being rate-limited (an email, a pending-auth id).
     * @param window How far back from now to count.
     * @return Number of failures recorded against [key] inside [window].
     */
    public suspend fun failuresWithin(key: String, window: Duration): Long

    /**
     * Marks one more failure against [key], counted starting now, expiring after [window].
     *
     * @param key Identifies what is being rate-limited.
     * @param window How long this one failure counts toward a future [failuresWithin] call.
     */
    public suspend fun recordFailure(key: String, window: Duration)
}
