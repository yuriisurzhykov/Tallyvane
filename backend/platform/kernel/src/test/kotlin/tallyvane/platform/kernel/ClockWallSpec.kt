package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo

class ClockWallSpec :
    StringSpec(
        {
            "reads a time that lies inside a bracket taken around the call" {
                val before = kotlin.time.Clock.System.now()
                val read = Clock.Wall().now()
                val after = kotlin.time.Clock.System.now()

                read shouldBeGreaterThanOrEqualTo before
                read shouldBeLessThanOrEqualTo after
            }

            "never goes backwards between two reads" {
                val clock = Clock.Wall()

                val first = clock.now()
                val second = clock.now()

                second shouldBeGreaterThanOrEqualTo first
            }
        },
    )
