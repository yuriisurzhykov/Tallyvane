package tallyvane.platform.kernel

import kotlin.time.Instant

internal class ClockFake(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
