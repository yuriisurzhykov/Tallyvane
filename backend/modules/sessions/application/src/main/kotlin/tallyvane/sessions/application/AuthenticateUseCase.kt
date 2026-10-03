package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Finds out whose request this is, from the secret in its cookie, on every request (ADR-079).
 *
 * The lifetimes in force are applied to the session as it is now, so a policy tightened this morning
 * reaches the sessions issued last week. A session found to be over is forgotten on the spot, which is
 * all the cleaning of expired sessions there is for now. The judging itself is [Recognition]'s.
 */
public interface AuthenticateUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     */
    public suspend fun resolve(session: Secret?): Resolution

    public class Authenticate(private val recognition: Recognition, private val transactions: TransactionRunner) :
        AuthenticateUseCase {
        override suspend fun resolve(session: Secret?): Resolution {
            // A request with no cookie opens no transaction: the health probes must answer without a database.
            val secret = session ?: return Resolution.Anonymous()
            return transactions.inTransaction { Verdict.Commit(recognition.of(secret)) }
        }

        override fun toString(): String = "Authenticate($recognition)"
    }
}
