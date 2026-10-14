package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.Purpose
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * An administrator puts a kept version in force, or rolls back to an earlier one (ADR-098).
 *
 * The one dangerous act in the policy screens: the route behind it asks for a fresh proof of who the person is
 * before this runs (ADR-092). The activation is kept with who made it and why, next to the version it names.
 * Putting a version in force for the administrators' sign-in that would leave no administrator able to finish one
 * is refused, so an administrator cannot lock out every administrator, themselves included, by a click.
 */
public interface ActivatePolicyVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param purpose The word of the purpose, such as `login`.
     * @param number The number of the version to put in force.
     * @param comment Why, in the administrator's words.
     */
    public suspend fun activate(
        administrator: AccountId,
        purpose: String,
        number: Int,
        comment: String,
    ): PolicyVersionActivated

    public class ActivatePolicyVersion(
        private val admins: Admins,
        private val versions: PolicyVersions,
        private val reach: Reach,
        private val words: PolicyWords,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ActivatePolicyVersionUseCase {
        override suspend fun activate(
            administrator: AccountId,
            purpose: String,
            number: Int,
            comment: String,
        ): PolicyVersionActivated = transactions.inTransaction {
            val named = words.purposeNamed(purpose)
            val change = Change.by(administrator, comment)
            val kept = named?.let { versions.history(it).numbered(number) }
            when {
                !admins.isAdmin(administrator) -> Verdict.Rollback(PolicyVersionActivated.Failed.NotAnAdministrator())

                named == null || change == null -> Verdict.Rollback(
                    PolicyVersionActivated.Failed.Invalid(
                        listOfNotNull(
                            words.unknownPurpose(purpose).takeIf { named == null },
                            words.invalidComment().takeIf { change == null },
                        ),
                    ),
                )

                kept == null -> Verdict.Rollback(PolicyVersionActivated.Failed.NoSuchVersion())

                named == Purpose.AdminLogin && reach.of(kept, named).shutsOutEveryone() ->
                    Verdict.Rollback(PolicyVersionActivated.Failed.WouldLockOutEveryAdministrator())

                else -> {
                    versions.activate(kept, change, clock.now())
                    Verdict.Commit(PolicyVersionActivated.Activated())
                }
            }
        }
    }
}
