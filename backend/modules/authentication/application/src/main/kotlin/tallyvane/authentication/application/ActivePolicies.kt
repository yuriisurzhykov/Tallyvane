package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The policy in force for each purpose, asked where an attempt stands.
 *
 * Every call asks [PolicyVersions] afresh, so a policy changed half-way through a sign-in applies to
 * its next step (ADR-078). Must be called inside a transaction, as the port is.
 *
 * The account's enrollment is the real one as soon as the attempt says whose account it is: the owner is
 * read from the attempt itself, and what that account has set up is read from where it is kept
 * (ADR-093). Before Google has vouched for anybody it is [Enrollment.Unknown], and only the steps every
 * account can pass apply.
 */
public class ActivePolicies(
    private val versions: PolicyVersions,
    private val owners: AttemptOwners,
    private val enrollments: Enrollments,
) {
    /**
     * Where [attempt], begun for [purpose], stands at [now].
     *
     * @throws IllegalStateException when no version is in force for [purpose]. The migrations put
     * one in force for every purpose, so this is a database somebody emptied by hand.
     */
    public fun progressOf(attempt: Attempt, purpose: Purpose, now: Instant): Progress {
        val enrollment = owners.of(attempt)?.let(enrollments::of) ?: Enrollment.Unknown
        return versionFor(purpose).progressOf(attempt, enrollment, now)
    }

    /**
     * The pause the policy in force for [purpose] gives after the first wrong answer, which is also where
     * the pause an account earns by its own wrong codes starts from (ADR-082).
     */
    public fun firstDelayOf(purpose: Purpose): Duration {
        val heard = Limits()
        versionFor(purpose).writeTo(heard)
        return heard.firstDelay()
    }

    private fun versionFor(purpose: Purpose): PolicyVersion = checkNotNull(versions.active(purpose)) {
        "No sign-in policy is in force for $purpose. The migrations activate one for every purpose; " +
            "activate a version again rather than letting anyone sign in without one."
    }

    override fun toString(): String = "ActivePolicies(versions=$versions)"

    /**
     * Listens for the one number of a policy this class wants.
     */
    private class Limits : PolicyVersion.Record {
        private var delay: Duration? = null

        fun firstDelay(): Duration = checkNotNull(delay) { "A policy version always tells its limits." }

        override fun number(number: Int) = Unit

        override fun purpose(purpose: Purpose) = Unit

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            delay = firstDelay
        }
    }
}
