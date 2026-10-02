package tallyvane.platform.idempotency

import tallyvane.platform.kernel.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] that stands still until a test moves it, for cases about what happens after a day.
 *
 * `ClockFake` in `platform:kernel` never moves, which is right for a rule evaluated at one instant.
 * A claim's life is a span of time, so the case has to be able to let it pass.
 */
class ClockSteppable(private var instant: Instant) : Clock {
    override fun now(): Instant = instant

    /**
     * Lets [by] pass.
     */
    fun advance(by: Duration) {
        instant += by
    }
}
