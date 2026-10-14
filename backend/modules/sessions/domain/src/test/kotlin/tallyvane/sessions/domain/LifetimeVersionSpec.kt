package tallyvane.sessions.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun lifetimes(
    client: ClientType = ClientType.Browser,
    idle: Duration = 1.days,
    absolute: Duration = 7.days,
    freshness: Duration = 5.minutes,
): ClientLifetimes = LifetimeDraft(client, idle, absolute, freshness).check().reportTo(
    object : LifetimeCheck.Report<ClientLifetimes> {
        override fun passed(lifetimes: ClientLifetimes): ClientLifetimes = lifetimes

        override fun rejected(violations: List<LifetimeViolation>): ClientLifetimes =
            throw AssertionError("Expected lifetimes within the bounds, but they broke $violations")
    },
)

private fun LifetimeVersion.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo(
        object : LifetimeVersion.Record {
            override fun number(number: Int) {
                told += "number $number"
            }

            override fun client(client: ClientType) {
                told += "client $client"
            }

            override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
                told += "lifetimes $idle $absolute $freshness"
            }
        },
    )
    return told
}

private val RULES = LifetimeRules.restore {
    it.lifetimes(ClientType.Browser, 1.days, 7.days, 5.minutes)
    it.lifetimes(ClientType.Admin, 1.hours, 8.hours, 5.minutes)
}

class LifetimeVersionSpec :
    StringSpec(
        {
            "a version tells its number first, then whose lifetimes they are, then the numbers" {
                LifetimeVersion(3, lifetimes(ClientType.Admin, 2.hours, 10.hours)).told() shouldBe listOf(
                    "number 3",
                    "client Admin",
                    "lifetimes 2h 10h 5m",
                )
            }

            "a version is numbered from 1" {
                shouldThrow<IllegalArgumentException> { LifetimeVersion(0, lifetimes()) }
            }

            "what a version told comes back as the same version" {
                val version = LifetimeVersion(2, lifetimes(idle = 3.days, absolute = 30.days))

                LifetimeVersion.restore { record -> version.writeTo(record) }.told() shouldBe version.told()
            }

            "a replay that breaks today's bounds is refused, loudly" {
                shouldThrow<IllegalStateException> {
                    LifetimeVersion.restore { record ->
                        record.number(1)
                        record.client(ClientType.Browser)
                        record.lifetimes(1.minutes, 7.days, 5.minutes)
                    }
                }
            }

            "a replay that leaves something out is refused" {
                shouldThrow<IllegalStateException> {
                    LifetimeVersion.restore { record ->
                        record.number(1)
                        record.client(ClientType.Browser)
                    }
                }
            }

            "a version put beside the rules changes its own kind of client and no other" {
                val beside = LifetimeVersion(2, lifetimes(ClientType.Admin, 2.hours, 10.hours)).beside(RULES)

                beside.toString() shouldBe
                    "LifetimeRules({Browser=Lifetimes(idle=1d, absolute=7d, freshness=5m), " +
                    "Admin=Lifetimes(idle=2h, absolute=10h, freshness=5m)})"
            }
        },
    )
