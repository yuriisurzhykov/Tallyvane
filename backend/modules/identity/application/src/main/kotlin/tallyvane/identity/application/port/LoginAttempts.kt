package tallyvane.identity.application.port

import kotlin.time.Duration

/**
 * How many recent failures identity's own rate limiting has recorded against one key — a sign-in
 * attempt by email today, a second-factor verification attempt by pending id later.
 *
 * The port this module owns instead of reaching for `platform:cache`'s `Counter` directly:
 * `application` may depend on nothing outside `own:domain`, `own:contract`, `platform:kernel` and
 * `platform:events` (`modules.yaml`) — a rate-limiting decorator that imported `platform:cache`
 * straight into `application` would be exactly the "a technology reaches past its adapter" case
 * `infrastructure` exists to prevent. The real implementation, over `Counter`, lives in
 * `identity:infrastructure`, which `modules.yaml` does let see `platform:*`.
 *
 * The key, the window and the threshold stay identity's own decision (`backend/.plans/backend-infra-cache-wiring.md`)
 * — this port only reports and records, it does not decide what a caller does with the number.
 */
public interface LoginAttempts {
    public suspend fun failuresWithin(key: String, window: Duration): Long

    public suspend fun recordFailure(key: String, window: Duration)
}
