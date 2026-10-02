package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Lifetimes
import tallyvane.sessions.domain.Standing
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Finds out whose request this is, from the secret in its cookie, on every request (ADR-079).
 *
 * The lifetimes are applied here, to the session as it is now, so a policy tightened this morning
 * reaches the sessions issued last week. A session found to be over is forgotten on the spot, which is
 * all the cleaning of expired sessions there is for now.
 */
public interface AuthenticateUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     */
    public suspend fun resolve(session: Secret?): Resolution

    public class Authenticate(
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SessionKeys,
        private val lifetimes: Lifetimes,
    ) : AuthenticateUseCase {
        override suspend fun resolve(session: Secret?): Resolution {
            val secret = session ?: return Resolution.Anonymous()
            val key = keys.keyOf(secret)
            return transactions.inTransaction {
                val now = clock.now()
                val standing = sessions.find(key)?.standingAt(now, lifetimes)
                Verdict.Commit(standing?.reportTo(Judging(key, now)) ?: Resolution.Lapsed())
            }
        }

        /**
         * A live session is noted as used and speaks for its person; one that is over is forgotten.
         */
        private inner class Judging(private val key: Digest, private val now: Instant) : Standing.Report<Resolution> {
            override fun live(account: Uuid, factors: Set<Factor>, authenticatedAt: Instant): Resolution {
                sessions.saw(key, now)
                return Resolution.SignedIn(AccountId(account))
            }

            override fun endedByIdleness(): Resolution = ended()

            override fun endedByAge(): Resolution = ended()

            private fun ended(): Resolution {
                sessions.forget(key)
                return Resolution.Lapsed()
            }
        }

        override fun toString(): String = "Authenticate(sessions=$sessions)"
    }
}
