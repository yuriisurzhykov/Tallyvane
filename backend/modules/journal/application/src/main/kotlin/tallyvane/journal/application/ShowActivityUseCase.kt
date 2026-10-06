package tallyvane.journal.application

import tallyvane.identity.contract.AccountId
import tallyvane.journal.application.port.Entries
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A signed-in person reads the journal of their own account (ADR-083).
 *
 * Only their own: the account comes from who is asking, never from the request. Reading writes nothing.
 */
public interface ShowActivityUseCase : UseCase {
    /**
     * @param before The cursor an earlier page gave, or null for the newest entries.
     */
    public suspend fun show(account: AccountId, before: String?): ActivityOutcome

    public class ShowActivity(private val entries: Entries, private val transactions: TransactionRunner) :
        ShowActivityUseCase {
        override suspend fun show(account: AccountId, before: String?): ActivityOutcome {
            val cursor = before?.let { it.toLongOrNull()?.takeIf { number -> number > 0 } }
            if (before != null && cursor == null) {
                return ActivityOutcome.Failed.UnknownCursor()
            }
            return transactions.inTransaction {
                Verdict.Commit(ActivityOutcome.Shown(entries.page(account.value, cursor, PAGE)))
            }
        }

        override fun toString(): String = "ShowActivity(entries=$entries)"

        private companion object {
            /**
             * How many entries a page holds.
             */
            const val PAGE = 30
        }
    }
}
