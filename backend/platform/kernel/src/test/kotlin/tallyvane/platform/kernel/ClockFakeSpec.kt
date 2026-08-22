package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class ClockFakeSpec :
    StringSpec({
        "returns the pinned instant" {
            val instant = Instant.parse("2026-01-01T00:00:00Z")
            ClockFake(instant).now() shouldBe instant
        }
    })
