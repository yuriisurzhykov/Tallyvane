package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeCheck
import tallyvane.sessions.domain.LifetimeDraft
import tallyvane.sessions.domain.LifetimeViolation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Lifetimes built the only way production builds them, through a [LifetimeDraft], for suites that need some to keep.
 *
 * Fails the test, naming the violations, if the values given are not within the bounds: a suite about storage has
 * no business passing values the domain would refuse.
 */
class CheckedLifetimes(
    private val client: ClientType,
    private val idle: Duration = 1.days,
    private val absolute: Duration = 7.days,
    private val freshness: Duration = 5.minutes,
) : LifetimeCheck.Report<ClientLifetimes> {
    /**
     * The lifetimes these values describe.
     */
    fun lifetimes(): ClientLifetimes = LifetimeDraft(client, idle, absolute, freshness).check().reportTo(this)

    override fun passed(lifetimes: ClientLifetimes): ClientLifetimes = lifetimes

    override fun rejected(violations: List<LifetimeViolation>): ClientLifetimes =
        throw AssertionError("Expected these values to be within the bounds, but they broke: $violations")
}
