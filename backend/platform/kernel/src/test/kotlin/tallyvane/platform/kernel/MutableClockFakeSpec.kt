package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MutableClockFakeSpec :
    StringSpec({
        "returns the pinned instant until moved" {
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            clock.now() shouldBe Instant.parse("2026-01-01T00:00:00Z")
        }

        "advance moves now forward by exactly the given duration" {
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            clock.advance(15.minutes)
            clock.now() shouldBe Instant.parse("2026-01-01T00:15:00Z")
        }

        "advancing twice accumulates" {
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            clock.advance(15.minutes)
            clock.advance(15.minutes)
            clock.now() shouldBe Instant.parse("2026-01-01T00:30:00Z")
        }
    })
