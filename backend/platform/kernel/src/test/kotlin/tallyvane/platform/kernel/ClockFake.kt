package tallyvane.platform.kernel

import kotlin.time.Instant

/**
 * A [Clock] that always returns [instant].
 *
 * Lives in `src/test` so it does not ship in the production jar (ADR-044).
 */
internal class ClockFake(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
