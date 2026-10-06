package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.TotpStanding
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
            val standing = TotpStanding.of(enrollments.find(account), codes.of(account))
            Verdict.Commit(standing.reportTo(Showing))
        }

        private object Showing : TotpStanding.Report<SecondFactorShown> {
            override fun off(): SecondFactorShown = SecondFactorShown(SecondFactorShown.Standing.Off, 0)

            override fun active(codesLeft: Int): SecondFactorShown =
                SecondFactorShown(SecondFactorShown.Standing.Active, codesLeft)

            override fun retired(codesLeft: Int): SecondFactorShown =
                SecondFactorShown(SecondFactorShown.Standing.Retired, codesLeft)
        }
    }
}
