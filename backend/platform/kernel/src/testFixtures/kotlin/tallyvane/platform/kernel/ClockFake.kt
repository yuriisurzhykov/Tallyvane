package tallyvane.platform.kernel

import kotlin.time.Instant

/**
 * A [Clock] that always returns [instant].
 *
 * Lives in `src/testFixtures` rather than `src/main`, so it never ships in the
 * production jar (ADR-044), and rather than `src/test`, so another module's tests
 * can pin time without declaring their own clock.
 */
class ClockFake(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
