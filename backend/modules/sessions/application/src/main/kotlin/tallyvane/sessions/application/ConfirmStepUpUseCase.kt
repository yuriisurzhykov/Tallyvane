package tallyvane.sessions.application

import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.contract.SignIns
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Freshness
import tallyvane.sessions.domain.SessionId
import kotlin.time.Instant

/**
 * A person who is signed in proves who they are again, for a dangerous act (ADR-092).
 *
 * Takes the finished confirmation from `authentication` and moves the time of the last proof on the
 * session in use, in one transaction: either the confirmation is spent and the session counts it, or
 * neither happened. The confirmation must be the signed-in person's own; one that is another account's is
 * spent all the same, so it cannot be tried again, and changes nothing.
 *
 * No new session is made and no secret changes: confirming does not make a session live longer, and it
 * does not change who holds it.
 */
public interface ConfirmStepUpUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     * @param surface The door the request came through: a session of the other door confirms nothing here.
     */
    public suspend fun confirm(session: Secret?, attempt: Secret?, surface: Surface): Confirmed

    public class ConfirmStepUp(
        private val recognition: Recognition,
        private val signIns: SignIns,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
        private val keys: SessionKeys,
    ) : ConfirmStepUpUseCase {
        override suspend fun confirm(session: Secret?, attempt: Secret?, surface: Surface): Confirmed =
            transactions.inTransaction {
                // Always committed: a confirmation of another account is spent and must stay spent, and a
                // session found to be over is forgotten by the recognition.
                Verdict.Commit(recognition.of(session, surface).reportTo(Presenting(session, attempt)))
            }

        /**
         * Each way a session can be: one in use takes the confirmation, any other has none to take it.
         */
        private inner class Presenting(private val secret: Secret?, private val attempt: Secret?) :
            Resolution.Report<Confirmed> {
            override fun signedIn(account: AccountId, session: SessionId, freshness: Freshness): Confirmed {
                val confirmation = attempt ?: return Confirmed.Failed.NothingToConfirm()
                return signIns.redeemStepUp(confirmation).reportTo(Taking(account, checkNotNull(secret)))
            }

            override fun lapsed(): Confirmed = Confirmed.Failed.SessionExpired()

            override fun anonymous(): Confirmed = Confirmed.Failed.SignInRequired()
        }

        /**
         * What to do with each answer `authentication` can give: keep the proof on the session of its own
         * person, and nothing for anyone else or for nothing.
         */
        private inner class Taking(private val account: AccountId, private val session: Secret) :
            Redemption.Report<Confirmed> {
            override fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): Confirmed {
                val key = keys.keyOf(session)
                val kept = sessions.find(key)
                return when {
                    account != this.account -> Confirmed.Failed.WrongAccount()
                    kept == null -> Confirmed.Failed.SessionExpired()
                    else -> {
                        sessions.confirm(key, kept.confirmed(authenticatedAt, proofs.mapTo(mutableSetOf(), ::factorOf)))
                        Confirmed.Done()
                    }
                }
            }

            override fun nothingToRedeem(): Confirmed = Confirmed.Failed.NothingToConfirm()
        }

        private fun factorOf(proof: Proof): Factor = when (proof) {
            Proof.Google -> Factor.Google
            Proof.Totp -> Factor.Totp
            Proof.RecoveryCode -> Factor.RecoveryCode
        }

        override fun toString(): String = "ConfirmStepUp($sessions)"
    }
}
