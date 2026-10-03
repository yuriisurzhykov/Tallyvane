package tallyvane.sessions.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun rules(idle: Duration, absolute: Duration): LifetimeRules =
    LifetimeRules.restore { it.lifetimes(ClientType.Browser, idle, absolute) }

class LifetimeRulesSpec :
    StringSpec(
        {
            "builds the lifetimes of the browser, a day idle and a week at most, that sessions start with" {
                rules(1.days, 7.days).longest(ClientType.Browser) shouldBe 7.days
            }

            "accepts the ends of the bounds the code sets" {
                rules(15.minutes, 15.minutes).longest(ClientType.Browser) shouldBe 15.minutes
                rules(30.days, 90.days).longest(ClientType.Browser) shouldBe 90.days
            }

            "refuses an idle limit shorter than 15 minutes" {
                shouldThrow<IllegalArgumentException> { rules(15.minutes - 1.minutes, 7.days) }
            }

            "refuses an idle limit longer than 30 days" {
                shouldThrow<IllegalArgumentException> { rules(30.days + 1.hours, 90.days) }
            }

            "refuses an absolute limit longer than 90 days, so no policy makes a session live ten years" {
                shouldThrow<IllegalArgumentException> { rules(1.days, 90.days + 1.hours) }
            }

            "refuses an absolute limit shorter than the idle limit, which the idle limit could never reach" {
                shouldThrow<IllegalArgumentException> { rules(2.days, 1.days) }
            }

            "refuses rules that leave a kind of client without lifetimes" {
                shouldThrow<IllegalStateException> { LifetimeRules.restore { } }
            }

            "refuses rules that give a kind of client two pairs" {
                shouldThrow<IllegalStateException> {
                    LifetimeRules.restore {
                        it.lifetimes(ClientType.Browser, 1.days, 7.days)
                        it.lifetimes(ClientType.Browser, 2.days, 7.days)
                    }
                }
            }
        },
    )
