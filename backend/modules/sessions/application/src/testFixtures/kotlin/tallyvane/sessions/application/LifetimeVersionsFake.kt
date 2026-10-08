package tallyvane.sessions.application

import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeRules
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * [LifetimeVersions] holding one set of rules a test can change between requests: the browser's day,
 * week and five minutes of freshness to begin with, and an administrator's hour, eight hours and five
 * minutes, as the migrations put in force.
 */
class LifetimeVersionsFake : LifetimeVersions {
    private var rules: LifetimeRules = browserOf(1.days, 7.days, 5.minutes)

    override fun active(): LifetimeRules = rules

    /**
     * Puts a version in force that gives a browser [idle] and [absolute], as an activation would.
     */
    fun activate(idle: Duration, absolute: Duration, freshness: Duration = 5.minutes) {
        rules = browserOf(idle, absolute, freshness)
    }

    override fun toString(): String = "LifetimeVersionsFake($rules)"

    private fun browserOf(idle: Duration, absolute: Duration, freshness: Duration): LifetimeRules =
        LifetimeRules.restore {
            it.lifetimes(ClientType.Browser, idle, absolute, freshness)
            it.lifetimes(ClientType.Admin, 1.hours, 8.hours, 5.minutes)
        }
}
