package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * The settings screen asks what the signed-in person has set up as a second factor.
 */
public interface ShowSecondFactorUseCase : UseCase {
    public suspend fun show(account: AccountId): SecondFactorShown

    public class ShowSecondFactor(
        private val enrollments: TotpEnrollments,
        private val codes: RecoveryCodeSets,
        private val transactions: TransactionRunner,
    ) : ShowSecondFactorUseCase {
        override suspend fun show(account: AccountId): SecondFactorShown = transactions.inTransaction {
            val enrollment = enrollments.find(account)
            val left = codes.of(account)?.remaining() ?: 0
            Verdict.Commit(
                when {
                    enrollment?.isActive() == true -> SecondFactorShown(SecondFactorShown.Standing.Active, left)
                    enrollment?.isRetired() == true -> SecondFactorShown(SecondFactorShown.Standing.Retired, left)
                    else -> SecondFactorShown(SecondFactorShown.Standing.Off, 0)
                },
            )
        }
    }
}
