package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * A signed-in person asks for ten new recovery codes, which replace every one they had, spent or not
 * (ADR-082, ADR-093).
 *
 * A route calls this only for someone who proved who they are recently (ADR-092). The journal is told in the
 * same transaction (ADR-095).
 */
public interface RegenerateRecoveryCodesUseCase : UseCase {
    public suspend fun regenerate(account: AccountId, session: Uuid): CodesRegenerated

    public class RegenerateRecoveryCodes(
        private val enrollments: TotpEnrollments,
        private val codes: RecoveryCodeSets,
        private val mint: RecoveryCodeMint,
        private val words: RecoveryCodeWords,
        private val digests: Digests,
        private val journal: SecurityJournal,
        private val transactions: TransactionRunner,
    ) : RegenerateRecoveryCodesUseCase {
        override suspend fun regenerate(account: AccountId, session: Uuid): CodesRegenerated =
            transactions.inTransaction {
                if (enrollments.lock(account)?.isActive() != true) {
                    Verdict.Rollback(CodesRegenerated.Failed.NotActive())
                } else {
                    val shown = mint.mint()
                    codes.keep(account, RecoveryCodes.issue(shown.map { digests.of(words.normalised(it.revealed())) }))
                    journal.recoveryCodesReissued(account, session)
                    Verdict.Commit(CodesRegenerated.Regenerated(shown))
                }
            }
    }
}
