package tallyvane.authentication.application

import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * A signed-in person turns TOTP off (ADR-082, ADR-093).
 *
 * Removes the enrolment, pending, active or retired, and the recovery codes with it. A route calls this
 * only for someone who proved who they are recently (ADR-092). The journal is told in the same transaction
 * (ADR-095), with the session the request came from, so the entry can name the device.
 */
public interface DisableTotpUseCase : UseCase {
    public suspend fun disable(account: AccountId, session: Uuid): TotpDisabled

    public class DisableTotp(
        private val enrollments: TotpEnrollments,
        private val journal: SecurityJournal,
        private val transactions: TransactionRunner,
    ) : DisableTotpUseCase {
        override suspend fun disable(account: AccountId, session: Uuid): TotpDisabled = transactions.inTransaction {
            if (enrollments.lock(account) == null) {
                Verdict.Rollback(TotpDisabled.Failed.NotEnabled())
            } else {
                enrollments.forget(account)
                journal.totpTurnedOff(account, session)
                Verdict.Commit(TotpDisabled.Disabled())
            }
        }
    }
}
