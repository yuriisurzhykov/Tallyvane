package tallyvane.sessions.application

import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeRules
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * [LifetimeVersions] holding one set of rules a test can change between requests: the browser's day and
 * week to begin with, as the migration puts in force.
 */
class LifetimeVersionsFake : LifetimeVersions {
    private var rules: LifetimeRules = browserOf(1.days, 7.days)

    override fun active(): LifetimeRules = rules

    /**
     * Puts a version in force that gives a browser [idle] and [absolute], as an activation would.
     */
    fun activate(idle: Duration, absolute: Duration) {
        rules = browserOf(idle, absolute)
    }

    override fun toString(): String = "LifetimeVersionsFake($rules)"

    private fun browserOf(idle: Duration, absolute: Duration): LifetimeRules =
        LifetimeRules.restore { it.lifetimes(ClientType.Browser, idle, absolute) }
}
