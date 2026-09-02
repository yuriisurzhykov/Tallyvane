package tallyvane.platform.kernel

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] a test can move forward, for a case that depends on time passing rather than on one
 * pinned instant — a rate-limit window closing, a token expiring.
 *
 * [ClockFake] already covers the more common shape, a single fixed "now" for the whole case. This
 * one exists for the case that needs a second, later "now" without constructing a second clock —
 * which would also mean constructing a second collaborator under test, losing whatever state the
 * first one had accumulated.
 */
public class MutableClockFake(private var instant: Instant) : Clock {
    override fun now(): Instant = instant

    /**
     * Moves this clock forward by [duration].
     *
     * There is no way to move it backward: a clock a test can rewind would let a case describe a
     * state wall-clock time can never actually reach.
     */
    public fun advance(duration: Duration) {
        instant += duration
    }
}
