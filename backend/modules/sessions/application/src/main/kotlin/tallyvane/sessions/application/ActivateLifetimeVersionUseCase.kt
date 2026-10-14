package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions

/**
 * An administrator puts a kept version of the lifetimes in force, or rolls back to an earlier one (ADR-098).
 *
 * A dangerous act: the route behind it asks for a fresh proof of who the person is before this runs (ADR-092). The
 * activation is kept with who made it and why, next to the version it names. The bounds the code sets keep any
 * version from ending sessions faster than a quarter of an hour of sitting unused, so none can shut everyone out
 * at once; what it will end is told by the preview.
 */
public interface ActivateLifetimeVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param client The word of the kind of client, such as `browser`.
     * @param number The number of the version to put in force.
     * @param comment Why, in the administrator's words.
     */
    public suspend fun activate(
        administrator: AccountId,
        client: String,
        number: Int,
        comment: String,
    ): LifetimeVersionActivated

    public class ActivateLifetimeVersion(
        private val admins: Admins,
        private val versions: LifetimeVersions,
        private val words: LifetimeWords,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ActivateLifetimeVersionUseCase {
        override suspend fun activate(
            administrator: AccountId,
            client: String,
            number: Int,
            comment: String,
        ): LifetimeVersionActivated = transactions.inTransaction {
            val named = words.clientNamed(client)
            val change = Change.by(administrator, comment)
            val kept = named?.let { versions.history(it).numbered(number) }
            when {
                !admins.isAdmin(administrator) -> Verdict.Rollback(LifetimeVersionActivated.Failed.NotAnAdministrator())

                named == null || change == null -> Verdict.Rollback(
                    LifetimeVersionActivated.Failed.Invalid(
                        listOfNotNull(
                            words.unknownClient(client).takeIf { named == null },
                            words.invalidComment().takeIf { change == null },
                        ),
                    ),
                )

                kept == null -> Verdict.Rollback(LifetimeVersionActivated.Failed.NoSuchVersion())

                else -> {
                    versions.activate(kept, change, clock.now())
                    Verdict.Commit(LifetimeVersionActivated.Activated())
                }
            }
        }

        override fun toString(): String = "ActivateLifetimeVersion"
    }
}
