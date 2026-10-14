package tallyvane.sessions.application

import tallyvane.sessions.domain.LifetimeViolation
import kotlin.time.Duration

/**
 * Says each bound a draft broke as a [Fault], with the numbers the code sets.
 *
 * Durations are said in milliseconds, as the API takes them.
 */
internal class LifetimeFaults : LifetimeViolation.Report<Fault> {
    override fun idleOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): Fault = Fault(
        "idle_millis",
        "out-of-bounds",
        "A session may sit unused for ${allowed.start.inWholeMilliseconds} to " +
            "${allowed.endInclusive.inWholeMilliseconds} milliseconds, not ${given.inWholeMilliseconds}.",
    )

    override fun absoluteOutOfBounds(given: Duration, longest: Duration): Fault = Fault(
        "absolute_millis",
        "out-of-bounds",
        "A session may live at most ${longest.inWholeMilliseconds} milliseconds, not ${given.inWholeMilliseconds}.",
    )

    override fun absoluteBelowIdle(absolute: Duration, idle: Duration): Fault = Fault(
        "absolute_millis",
        "below-idle",
        "A session may live at most ${absolute.inWholeMilliseconds} milliseconds, which is less than it may sit " +
            "unused (${idle.inWholeMilliseconds}): the limit for sitting unused could never be reached.",
    )

    override fun freshnessOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): Fault = Fault(
        "freshness_millis",
        "out-of-bounds",
        "A proof may stay fresh for ${allowed.start.inWholeMilliseconds} to " +
            "${allowed.endInclusive.inWholeMilliseconds} milliseconds, not ${given.inWholeMilliseconds}.",
    )
}
