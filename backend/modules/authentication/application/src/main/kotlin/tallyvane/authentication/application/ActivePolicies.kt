package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import kotlin.time.Instant

/**
 * The policy in force for each purpose, asked where an attempt stands.
 *
 * Every call asks [PolicyVersions] afresh, so a policy changed half-way through a sign-in applies to
 * its next step (ADR-078). Must be called inside a transaction, as the port is.
 *
 * The account's enrollment is [Enrollment.Unknown] for now: nothing can be set up before TOTP arrives
 * in slice 5, and until then every account's enrollment is the empty one.
 */
public class ActivePolicies(private val versions: PolicyVersions) {
    /**
     * Where [attempt], begun for [purpose], stands at [now].
     *
     * @throws IllegalStateException when no version is in force for [purpose]. The migrations put
     * one in force for every purpose, so this is a database somebody emptied by hand.
     */
    public fun progressOf(attempt: Attempt, purpose: Purpose, now: Instant): Progress {
        val version = checkNotNull(versions.active(purpose)) {
            "No sign-in policy is in force for $purpose. The migrations activate one for every purpose; " +
                "activate a version again rather than letting anyone sign in without one."
        }
        return version.progressOf(attempt, Enrollment.Unknown, now)
    }

    override fun toString(): String = "ActivePolicies(versions=$versions)"
}
