package tallyvane.authentication.application

import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A signed-in person turns TOTP off (ADR-082, ADR-093).
 *
 * Removes the enrolment, pending, active or retired, and the recovery codes with it. A route calls this
 * only for someone who proved who they are recently (ADR-092).
 */
public interface DisableTotpUseCase : UseCase {
    public suspend fun disable(account: AccountId): TotpDisabled

    public class DisableTotp(private val enrollments: TotpEnrollments, private val transactions: TransactionRunner) :
        DisableTotpUseCase {
        override suspend fun disable(account: AccountId): TotpDisabled = transactions.inTransaction {
            if (enrollments.lock(account) == null) {
                Verdict.Rollback(TotpDisabled.Failed.NotEnabled())
            } else {
                enrollments.forget(account)
                Verdict.Commit(TotpDisabled.Disabled())
            }
        }
    }
}
