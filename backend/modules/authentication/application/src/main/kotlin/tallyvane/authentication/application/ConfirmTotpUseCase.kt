package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.CodeVerdict
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * A signed-in person types the first code from their app, which turns TOTP on (ADR-093).
 *
 * The right code makes the pending enrolment active and issues ten recovery codes in the same
 * transaction, replacing any set the account had. The code itself is the proof, so a route asks for
 * nothing fresher than a signed-in person. A wrong code changes nothing: no limit applies, since whoever
 * began this enrolment already knows its seed. The journal is told in the same transaction (ADR-095).
 */
public interface ConfirmTotpUseCase : UseCase {
    public suspend fun confirm(account: AccountId, session: Uuid, code: String): TotpConfirmed

    public class ConfirmTotp(
        private val enrollments: TotpEnrollments,
        private val codes: RecoveryCodeSets,
        private val mint: RecoveryCodeMint,
        private val words: RecoveryCodeWords,
        private val digests: Digests,
        private val transactions: TransactionRunner,
        private val journal: SecurityJournal,
        private val clock: Clock,
    ) : ConfirmTotpUseCase {
        override suspend fun confirm(account: AccountId, session: Uuid, code: String): TotpConfirmed =
            transactions.inTransaction {
                val pending = enrollments.lock(account)?.takeIf { it.isPending() }
                val result = pending?.confirm(code, clock.now())?.reportTo(Confirming(account, session))
                    ?: TotpConfirmed.Failed.NotBegun()
                when (result) {
                    is TotpConfirmed.Confirmed -> Verdict.Commit(result)
                    is TotpConfirmed.Failed -> Verdict.Rollback(result)
                }
            }

        private inner class Confirming(private val account: AccountId, private val session: Uuid) :
            CodeVerdict.Report<TotpConfirmed> {
            override fun accepted(next: TotpEnrollment): TotpConfirmed {
                enrollments.keep(account, next)
                val shown = mint.mint()
                codes.keep(account, RecoveryCodes.issue(shown.map { digests.of(words.normalised(it.revealed())) }))
                journal.totpTurnedOn(account, session)
                return TotpConfirmed.Confirmed(shown)
            }

            override fun wrong(): TotpConfirmed = TotpConfirmed.Failed.WrongCode()
        }
    }
}
