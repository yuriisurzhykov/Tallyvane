package tallyvane.identity.application

import tallyvane.identity.application.port.KeptAccounts
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A signed-in person asks who they are.
 */
public interface WhoAmIUseCase : UseCase {
    public suspend fun whoIs(account: AccountId): WhoAmIOutcome

    public class WhoAmI(private val kept: KeptAccounts, private val transactions: TransactionRunner) : WhoAmIUseCase {
        override suspend fun whoIs(account: AccountId): WhoAmIOutcome = transactions.inTransaction {
            Verdict.Commit(
                kept.profileOf(account.value)?.let { WhoAmIOutcome.Known(it) } ?: WhoAmIOutcome.Failed.Gone(),
            )
        }

        override fun toString(): String = "WhoAmI(kept=$kept)"
    }
}
