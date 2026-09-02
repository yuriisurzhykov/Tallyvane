package tallyvane.platform.cache

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.MutableClockFake
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CounterInMemorySpec :
    StringSpec({
        "the first occurrence of a key starts its count at 1" {
            val counter = Counter.InMemory(MutableClockFake(Instant.parse("2026-01-01T00:00:00Z")))
            counter.increment("identity:sign-in:a@example.com", 15.minutes) shouldBe 1
        }

        "a second occurrence inside the window adds to the same count" {
            val counter = Counter.InMemory(MutableClockFake(Instant.parse("2026-01-01T00:00:00Z")))
            counter.increment("identity:sign-in:a@example.com", 15.minutes)
            counter.increment("identity:sign-in:a@example.com", 15.minutes) shouldBe 2
        }

        "two different keys are counted independently" {
            val counter = Counter.InMemory(MutableClockFake(Instant.parse("2026-01-01T00:00:00Z")))
            counter.increment("identity:sign-in:a@example.com", 15.minutes)
            counter.increment("identity:sign-in:a@example.com", 15.minutes)
            counter.increment("identity:sign-in:b@example.com", 15.minutes) shouldBe 1
        }

        "an occurrence after the window has fully elapsed starts a fresh count" {
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            val counter = Counter.InMemory(clock)
            counter.increment("identity:sign-in:a@example.com", 15.minutes)
            counter.increment("identity:sign-in:a@example.com", 15.minutes)

            clock.advance(16.minutes)

            counter.increment("identity:sign-in:a@example.com", 15.minutes) shouldBe 1
        }

        "an occurrence exactly at the window's length counts the window as closed" {
            // The window measures how long a count stays valid; at exactly that length elapsed,
            // it no longer does. Pinned here because "closed" could as reasonably have meant
            // "one more instant past the length", and the two disagree at this exact boundary.
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            val counter = Counter.InMemory(clock)
            counter.increment("identity:sign-in:a@example.com", 15.minutes)

            clock.advance(15.minutes)

            counter.increment("identity:sign-in:a@example.com", 15.minutes) shouldBe 1
        }

        "an occurrence one instant before the window's length still belongs to it" {
            val clock = MutableClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            val counter = Counter.InMemory(clock)
            counter.increment("identity:sign-in:a@example.com", 15.minutes)

            clock.advance(15.minutes.minus(1.minutes))

            counter.increment("identity:sign-in:a@example.com", 15.minutes) shouldBe 2
        }
    })
