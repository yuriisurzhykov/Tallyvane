package tallyvane.identity.domain.token

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How far back a refresh token's own lineage may be redeemed before this module simply stops
 * recognising it — RFC 9700 §4.14.2's absolute cap, distinct from a token's own idle timeout: a
 * family kept alive by continuous rotation every few days still stops working once its first
 * token is old enough, forcing a fresh sign-in no amount of activity postpones.
 *
 * ```
 * val policy = RefreshTokenRetentionPolicy.Default(90.days)
 * policy.cutoff(now = Instant.parse("2026-04-01T00:00:00Z")) // -> 2026-01-01T00:00:00Z
 * ```
 */
public interface RefreshTokenRetentionPolicy {
    /**
     * Every refresh token issued before this instant is past the absolute cap as of [now], and a
     * store deleting rows with `issued_at` before it is deleting exactly the ones this policy
     * considers expired — nothing younger, nothing spared.
     */
    public fun cutoff(now: Instant): Instant

    /**
     * No I/O, so it nests here rather than living as a top-level type — the same reasoning
     * [RefreshRotationPolicy.Default] already states.
     */
    public class Default(private val absoluteCap: Duration) : RefreshTokenRetentionPolicy {
        override fun cutoff(now: Instant): Instant = now - absoluteCap
    }
}
