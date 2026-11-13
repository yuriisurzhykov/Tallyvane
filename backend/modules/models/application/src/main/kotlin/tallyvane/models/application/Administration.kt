package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.days

/**
 * What every use case here shares: one transaction, an administrator only, and the screen as it is after.
 */
public class Administration(
    private val book: ProviderBook,
    private val admins: Admins,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /**
     * Runs [change] for [administrator] and answers with the screen, or with what [change] refused.
     */
    public suspend fun run(administrator: AccountId, change: (ProviderBook) -> ModelsOutcome.Failed?): ModelsOutcome =
        transactions.inTransaction {
            val refused = if (admins.isAdmin(administrator)) change(book) else ModelsOutcome.Failed.NotAnAdministrator()
            if (refused == null) {
                Verdict.Commit(ModelsOutcome.Shown(book.list(), book.costs(clock.now() - WINDOW.days)))
            } else {
                Verdict.Rollback(refused)
            }
        }

    override fun toString(): String = "Administration($book)"

    private companion object {
        const val WINDOW = 30
    }
}
