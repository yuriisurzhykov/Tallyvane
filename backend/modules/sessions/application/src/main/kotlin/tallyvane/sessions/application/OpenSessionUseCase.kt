package tallyvane.sessions.application

import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.contract.SignIns
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Device
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Instant

/**
 * A person whose sign-in is complete asks for a session.
 *
 * Takes the completed sign-in from `authentication` and keeps a session under a secret nobody has seen
 * before, in one transaction: either the sign-in is gone and the session exists, or neither happened. A
 * secret that is new, never the sign-in's own, is what keeps someone who planted a known secret before
 * the sign-in from holding the session afterwards (ADR-079).
 */
public interface OpenSessionUseCase : UseCase {
    /**
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     * @param agent What the browser said about itself, which the session keeps as the device it is on.
     */
    public suspend fun open(attempt: Secret?, agent: UserAgent): Opened

    public class OpenSession(
        private val signIns: SignIns,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SessionKeys,
        private val lifetimes: LifetimeVersions,
    ) : OpenSessionUseCase {
        override suspend fun open(attempt: Secret?, agent: UserAgent): Opened {
            val secret = attempt ?: return Opened.Failed.NothingToOpen()
            return transactions.inTransaction {
                val outcome = signIns.redeem(secret).reportTo(Beginning(clock.now(), agent.device()))
                if (outcome is Opened.Issued) Verdict.Commit(outcome) else Verdict.Rollback(outcome)
            }
        }

        /**
         * What to do with each answer `authentication` can give: keep a session for a sign-in, and
         * for nothing, nothing.
         */
        private inner class Beginning(private val now: Instant, private val device: Device) :
            Redemption.Report<Opened> {
            override fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): Opened {
                val issued = keys.issue()
                sessions.add(
                    issued.key,
                    Session.begin(
                        issued.id,
                        account.value,
                        proofs.mapTo(mutableSetOf(), ::factorOf),
                        ClientType.Browser,
                        device,
                        authenticatedAt,
                        now,
                    ),
                )
                return Opened.Issued(issued.secret, lifetimes.active().longest(ClientType.Browser))
            }

            override fun nothingToRedeem(): Opened = Opened.Failed.NothingToOpen()
        }

        private fun factorOf(proof: Proof): Factor = when (proof) {
            Proof.Google -> Factor.Google
            Proof.Totp -> Factor.Totp
            Proof.RecoveryCode -> Factor.RecoveryCode
        }

        override fun toString(): String = "OpenSession(sessions=$sessions)"
    }
}
