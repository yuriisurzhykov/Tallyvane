package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * An administrator proposes a new version of the policy of a purpose (ADR-098).
 *
 * The proposal is read into a draft and checked against the bounds the code sets (ADR-078): a policy outside them
 * is not kept, and the answer names every bound it breaks and every word that means nothing, so a form is fixed
 * once. A version that passes is kept with its author and comment and changes nothing about how anyone signs in
 * until [ActivatePolicyVersionUseCase] makes it the one in force.
 */
public interface AddPolicyVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param proposal The policy as the client wrote it.
     * @param comment Why, in the administrator's words.
     */
    public suspend fun add(administrator: AccountId, proposal: ProposedPolicy, comment: String): PolicyVersionAdded

    public class AddPolicyVersion(
        private val admins: Admins,
        private val versions: PolicyVersions,
        private val words: PolicyWords,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : AddPolicyVersionUseCase {
        override suspend fun add(
            administrator: AccountId,
            proposal: ProposedPolicy,
            comment: String,
        ): PolicyVersionAdded = transactions.inTransaction {
            val outcome: PolicyVersionAdded = if (admins.isAdmin(administrator)) {
                Drafting(words).of(proposal).reportTo(Keeping(Change.by(administrator, comment)))
            } else {
                PolicyVersionAdded.Failed.NotAnAdministrator()
            }
            when (outcome) {
                is PolicyVersionAdded.Failed -> Verdict.Rollback(outcome)
                is PolicyVersionAdded.Added -> Verdict.Commit(outcome)
            }
        }

        /**
         * Keeps a policy that passed, with the change that made it, and refuses everything else: every fault of the
         * proposal, and a missing comment, are told together.
         */
        private inner class Keeping(private val change: Change?) : Drafted.Report<PolicyVersionAdded> {
            override fun passed(policy: SignInPolicy): PolicyVersionAdded = if (change == null) {
                refused(emptyList())
            } else {
                val kept = versions.add(policy, change, clock.now())
                PolicyVersionAdded.Added(words.wordOf(Purposes().of(kept)), Numbered().of(kept))
            }

            override fun refused(faults: List<Fault>): PolicyVersionAdded {
                val all = faults + listOfNotNull(words.invalidComment().takeIf { change == null })
                return PolicyVersionAdded.Failed.Invalid(all)
            }
        }
    }
}
