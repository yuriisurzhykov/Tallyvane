package tallyvane.sessions.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun verdict(idle: Duration, absolute: Duration, freshness: Duration = 5.minutes): List<String> =
    LifetimeDraft(ClientType.Browser, idle, absolute, freshness).check().reportTo(
        object : LifetimeCheck.Report<List<String>> {
            override fun passed(lifetimes: ClientLifetimes): List<String> = emptyList()

            override fun rejected(violations: List<LifetimeViolation>): List<String> =
                violations.map { it.reportTo(Telling()) }
        },
    )

private class Telling : LifetimeViolation.Report<String> {
    override fun idleOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): String =
        "idle $given not in ${allowed.start}..${allowed.endInclusive}"

    override fun absoluteOutOfBounds(given: Duration, longest: Duration): String = "absolute $given over $longest"

    override fun absoluteBelowIdle(absolute: Duration, idle: Duration): String = "absolute $absolute below idle $idle"

    override fun freshnessOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): String =
        "freshness $given not in ${allowed.start}..${allowed.endInclusive}"
}

class LifetimeDraftSpec :
    StringSpec(
        {
            "lifetimes within the bounds pass" {
                verdict(1.days, 7.days) shouldBe emptyList()
            }

            "the ends of the bounds pass" {
                verdict(15.minutes, 15.minutes, freshness = 1.minutes) shouldBe emptyList()
                verdict(30.days, 90.days, freshness = 15.minutes) shouldBe emptyList()
            }

            "an idle limit outside 15 minutes to 30 days is told with the bounds" {
                verdict(14.minutes, 7.days) shouldBe listOf("idle 14m not in 15m..30d")
                verdict(30.days + 1.hours, 90.days) shouldBe listOf("idle 30d 1h not in 15m..30d")
            }

            "an absolute limit over 90 days is told with the longest" {
                verdict(1.days, 90.days + 1.hours) shouldBe listOf("absolute 90d 1h over 90d")
            }

            "an absolute limit under the idle limit is told, since the idle limit could never be reached" {
                verdict(2.days, 1.days) shouldBe listOf("absolute 1d below idle 2d")
            }

            "freshness outside one to fifteen minutes is told with the bounds" {
                verdict(1.days, 7.days, freshness = 59.seconds) shouldBe listOf("freshness 59s not in 1m..15m")
                verdict(1.days, 7.days, freshness = 16.minutes) shouldBe listOf("freshness 16m not in 1m..15m")
            }

            "everything wrong is told at once" {
                verdict(1.minutes, 30.seconds, freshness = 1.hours) shouldBe listOf(
                    "idle 1m not in 15m..30d",
                    "absolute 30s below idle 1m",
                    "freshness 1h not in 1m..15m",
                )
            }
        },
    )
