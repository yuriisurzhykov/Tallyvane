package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * An administrator reads the versions of the policy of one purpose (ADR-098).
 *
 * Every call asks `identity` afresh whether the account still holds the right, so taking it away works from the
 * next request and not from the end of a session. Reading writes nothing.
 */
public interface ListPolicyVersionsUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param purpose The word of the purpose whose versions are wanted, such as `login`.
     */
    public suspend fun list(administrator: AccountId, purpose: String): PolicyVersionsListed

    public class ListPolicyVersions(
        private val admins: Admins,
        private val versions: PolicyVersions,
        private val words: PolicyWords,
        private val transactions: TransactionRunner,
    ) : ListPolicyVersionsUseCase {
        override suspend fun list(administrator: AccountId, purpose: String): PolicyVersionsListed =
            transactions.inTransaction {
                val named = words.purposeNamed(purpose)
                when {
                    !admins.isAdmin(administrator) -> Verdict.Rollback(PolicyVersionsListed.Failed.NotAnAdministrator())

                    named == null -> Verdict.Rollback(
                        PolicyVersionsListed.Failed.Invalid(listOf(words.unknownPurpose(purpose))),
                    )

                    else -> Verdict.Commit(PolicyVersionsListed.Listed(versions.history(named)))
                }
            }
    }
}
