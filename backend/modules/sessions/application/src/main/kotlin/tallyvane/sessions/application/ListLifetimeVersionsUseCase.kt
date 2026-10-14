package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions

/**
 * An administrator reads the versions of the lifetimes of one kind of client (ADR-098).
 *
 * Every call asks `identity` afresh whether the account still holds the right, so taking it away works from the
 * next request and not from the end of a session. Reading writes nothing.
 */
public interface ListLifetimeVersionsUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param client The word of the kind of client whose versions are wanted, such as `browser`.
     */
    public suspend fun list(administrator: AccountId, client: String): LifetimeVersionsListed

    public class ListLifetimeVersions(
        private val admins: Admins,
        private val versions: LifetimeVersions,
        private val words: LifetimeWords,
        private val transactions: TransactionRunner,
    ) : ListLifetimeVersionsUseCase {
        override suspend fun list(administrator: AccountId, client: String): LifetimeVersionsListed =
            transactions.inTransaction {
                val named = words.clientNamed(client)
                when {
                    !admins.isAdmin(
                        administrator,
                    ) -> Verdict.Rollback(LifetimeVersionsListed.Failed.NotAnAdministrator())

                    named == null -> Verdict.Rollback(
                        LifetimeVersionsListed.Failed.Invalid(listOf(words.unknownClient(client))),
                    )

                    else -> Verdict.Commit(LifetimeVersionsListed.Listed(versions.history(named)))
                }
            }

        override fun toString(): String = "ListLifetimeVersions"
    }
}
