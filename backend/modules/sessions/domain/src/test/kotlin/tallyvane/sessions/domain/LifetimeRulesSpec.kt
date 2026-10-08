package tallyvane.sessions.domain

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun rules(idle: Duration, absolute: Duration, freshness: Duration = 5.minutes): LifetimeRules =
    LifetimeRules.restore {
        it.lifetimes(ClientType.Browser, idle, absolute, freshness)
        it.lifetimes(ClientType.Admin, 1.hours, 8.hours, 5.minutes)
    }

class LifetimeRulesSpec :
    StringSpec(
        {
            "builds the lifetimes of the browser, a day idle and a week at most, that sessions start with" {
                shouldNotThrowAny { rules(1.days, 7.days) }
            }

            "accepts the ends of the bounds the code sets" {
                shouldNotThrowAny { rules(15.minutes, 15.minutes) }
                shouldNotThrowAny { rules(30.days, 90.days) }
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

            "accepts a proof that stays fresh from one minute to fifteen" {
                shouldNotThrowAny { rules(1.days, 7.days, freshness = 1.minutes) }
                shouldNotThrowAny { rules(1.days, 7.days, freshness = 15.minutes) }
            }

            "refuses a proof that stays fresh for less than a minute, which no person could use" {
                shouldThrow<IllegalArgumentException> { rules(1.days, 7.days, freshness = 59.seconds) }
            }

            "refuses a proof that stays fresh for more than fifteen minutes, which would make the check empty" {
                shouldThrow<IllegalArgumentException> { rules(1.days, 7.days, freshness = 16.minutes) }
            }

            "refuses rules that leave a kind of client without lifetimes" {
                shouldThrow<IllegalStateException> { LifetimeRules.restore { } }
            }

            "refuses rules that leave the administrators without lifetimes" {
                shouldThrow<IllegalStateException> {
                    LifetimeRules.restore { it.lifetimes(ClientType.Browser, 1.days, 7.days, 5.minutes) }
                }
            }

            "refuses rules that give a kind of client two pairs" {
                shouldThrow<IllegalStateException> {
                    LifetimeRules.restore {
                        it.lifetimes(ClientType.Browser, 1.days, 7.days, 5.minutes)
                        it.lifetimes(ClientType.Admin, 1.hours, 8.hours, 5.minutes)
                        it.lifetimes(ClientType.Browser, 2.days, 7.days, 5.minutes)
                    }
                }
            }
        },
    )
