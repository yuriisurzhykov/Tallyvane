package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientLifetimes

/**
 * An administrator proposes a new version of the lifetimes of a kind of client (ADR-098).
 *
 * The proposal is read into a draft and checked against the bounds the code sets (ADR-078): lifetimes outside them
 * are not kept, and the answer names every bound they break and every word that means nothing, so a form is fixed
 * once. A version that passes is kept with its author and comment and changes nothing about how long anyone's
 * session lives until [ActivateLifetimeVersionUseCase] makes it the one in force.
 */
public interface AddLifetimeVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param proposal The lifetimes as the client wrote them.
     * @param comment Why, in the administrator's words.
     */
    public suspend fun add(
        administrator: AccountId,
        proposal: ProposedLifetimes,
        comment: String,
    ): LifetimeVersionAdded

    public class AddLifetimeVersion(
        private val admins: Admins,
        private val versions: LifetimeVersions,
        private val words: LifetimeWords,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : AddLifetimeVersionUseCase {
        override suspend fun add(
            administrator: AccountId,
            proposal: ProposedLifetimes,
            comment: String,
        ): LifetimeVersionAdded = transactions.inTransaction {
            val outcome: LifetimeVersionAdded = if (admins.isAdmin(administrator)) {
                LifetimeDrafting(words).of(proposal).reportTo(Keeping(Change.by(administrator, comment)))
            } else {
                LifetimeVersionAdded.Failed.NotAnAdministrator()
            }
            when (outcome) {
                is LifetimeVersionAdded.Failed -> Verdict.Rollback(outcome)
                is LifetimeVersionAdded.Added -> Verdict.Commit(outcome)
            }
        }

        override fun toString(): String = "AddLifetimeVersion"

        /**
         * Keeps lifetimes that passed, with the change that made them, and refuses everything else: every fault of
         * the proposal, and a missing comment, are told together.
         */
        private inner class Keeping(private val change: Change?) : Drafted.Report<LifetimeVersionAdded> {
            override fun passed(lifetimes: ClientLifetimes): LifetimeVersionAdded = if (change == null) {
                refused(emptyList())
            } else {
                val kept = versions.add(lifetimes, change, clock.now())
                LifetimeVersionAdded.Added(words.wordOf(Clients().of(kept)), Numbered().of(kept))
            }

            override fun refused(faults: List<Fault>): LifetimeVersionAdded {
                val all = faults + listOfNotNull(words.invalidComment().takeIf { change == null })
                return LifetimeVersionAdded.Failed.Invalid(all)
            }
        }
    }
}
