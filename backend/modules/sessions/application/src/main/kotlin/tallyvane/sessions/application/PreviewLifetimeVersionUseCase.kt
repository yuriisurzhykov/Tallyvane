package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions

/**
 * An administrator asks what a kept version of the lifetimes would do if it were put in force (ADR-098).
 *
 * The preview is always of a version that is kept: a new version is added first, which changes nothing for anyone,
 * and its consequences are read by its number. That is the same for a new version and for a rollback, and the server
 * keeps no draft. Reading writes nothing.
 */
public interface PreviewLifetimeVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param client The word of the kind of client, such as `browser`.
     * @param number The number of the version among the versions of the kind of client.
     */
    public suspend fun preview(administrator: AccountId, client: String, number: Int): LifetimeVersionPreviewed

    public class PreviewLifetimeVersion(
        private val admins: Admins,
        private val versions: LifetimeVersions,
        private val endings: Endings,
        private val words: LifetimeWords,
        private val transactions: TransactionRunner,
    ) : PreviewLifetimeVersionUseCase {
        override suspend fun preview(administrator: AccountId, client: String, number: Int): LifetimeVersionPreviewed =
            transactions.inTransaction {
                val named = words.clientNamed(client)
                val kept = named?.let { versions.history(it).numbered(number) }
                when {
                    !admins.isAdmin(administrator) ->
                        Verdict.Rollback(LifetimeVersionPreviewed.Failed.NotAnAdministrator())

                    named == null -> Verdict.Rollback(
                        LifetimeVersionPreviewed.Failed.Invalid(listOf(words.unknownClient(client))),
                    )

                    kept == null -> Verdict.Rollback(LifetimeVersionPreviewed.Failed.NoSuchVersion())

                    else -> Verdict.Commit(LifetimeVersionPreviewed.Previewed(endings.of(kept)))
                }
            }

        override fun toString(): String = "PreviewLifetimeVersion"
    }
}
