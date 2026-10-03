package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.CodeVerdict
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A signed-in person types the first code from their app, which turns TOTP on (ADR-093).
 *
 * The right code makes the pending enrolment active and issues ten recovery codes in the same
 * transaction, replacing any set the account had. The code itself is the proof, so a route asks for
 * nothing fresher than a signed-in person. A wrong code changes nothing: no limit applies, since whoever
 * began this enrolment already knows its seed.
 */
public interface ConfirmTotpUseCase : UseCase {
    public suspend fun confirm(account: AccountId, code: String): TotpConfirmed

    public class ConfirmTotp(
        private val enrollments: TotpEnrollments,
        private val codes: RecoveryCodeSets,
        private val mint: RecoveryCodeMint,
        private val words: RecoveryCodeWords,
        private val digests: Digests,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ConfirmTotpUseCase {
        override suspend fun confirm(account: AccountId, code: String): TotpConfirmed = transactions.inTransaction {
            val pending = enrollments.lock(account)?.takeIf { it.isPending() }
            if (pending == null) {
                Verdict.Rollback(TotpConfirmed.Failed.NotBegun())
            } else {
                pending.confirm(code, clock.now()).reportTo(Confirming(account))
            }
        }

        private inner class Confirming(private val account: AccountId) : CodeVerdict.Report<Verdict<TotpConfirmed>> {
            override fun accepted(next: TotpEnrollment): Verdict<TotpConfirmed> {
                enrollments.keep(account, next)
                val shown = mint.mint()
                codes.keep(account, RecoveryCodes.issue(shown.map { digests.of(words.normalised(it.revealed())) }))
                return Verdict.Commit(TotpConfirmed.Confirmed(shown))
            }

            override fun wrong(): Verdict<TotpConfirmed> = Verdict.Rollback(TotpConfirmed.Failed.WrongCode())
        }
    }
}
