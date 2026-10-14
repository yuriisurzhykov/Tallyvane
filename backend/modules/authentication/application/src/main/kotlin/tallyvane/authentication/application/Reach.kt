package tallyvane.authentication.application

import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.identity.contract.Accounts
import tallyvane.identity.contract.Admins

/**
 * Whom a version of a policy would reach, and how many of them it would shut out (ADR-098).
 *
 * The judge is the domain's own: the same rules that decide a sign-in decide whether a person with a given set of
 * factors could finish one, so a preview cannot say anything a sign-in would not. For the administrators' sign-in the
 * people are the administrators, each looked at on their own; for every other purpose they are all the accounts, and
 * the accounts are told apart by the factors they have set up, counted by the database in one query.
 *
 * Runs inside the caller's transaction, as the ports it reads do.
 */
public class Reach(
    private val admins: Admins,
    private val accounts: Accounts,
    private val enrollments: Enrollments,
    private val census: EnrollmentCensus,
) {
    /**
     * What [version], a version of the policy for [purpose], would do to the people it applies to.
     */
    public fun of(version: PolicyVersion, purpose: Purpose): Consequences =
        if (purpose == Purpose.AdminLogin) administrators(version) else everyone(version)

    private fun administrators(version: PolicyVersion): Consequences {
        val people = admins.all()
        return Consequences(people.size, people.count { !version.canBePassedBy(enrollments.of(it)) })
    }

    private fun everyone(version: PolicyVersion): Consequences {
        val people = accounts.count()
        val groups = census.enrolled()
        val withNothing = (people - groups.values.sum()).coerceAtLeast(0)
        val shutOutWithNothing = if (version.canBePassedBy(NOTHING_SET_UP)) 0 else withNothing
        val shutOut = groups.filterKeys { !version.canBePassedBy(it) }.values.sum() + shutOutWithNothing
        return Consequences(people, shutOut.coerceAtMost(people))
    }

    override fun toString(): String = "Reach"

    private companion object {
        val NOTHING_SET_UP = Enrollment(emptySet())
    }
}
