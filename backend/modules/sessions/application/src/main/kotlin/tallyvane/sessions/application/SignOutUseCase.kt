package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions

/**
 * A person signs out: the session they present is forgotten, so the next request carrying its secret
 * is a stranger's (ADR-079).
 *
 * Signing out when there is nothing to sign out of is not a failure: the person wanted to be signed
 * out, and is.
 */
public interface SignOutUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     */
    public suspend fun signOut(session: Secret?)

    public class SignOut(
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
        private val keys: SessionKeys,
    ) : SignOutUseCase {
        override suspend fun signOut(session: Secret?) {
            if (session != null) {
                transactions.inTransaction {
                    sessions.forget(keys.keyOf(session))
                    Verdict.Commit(Unit)
                }
            }
        }

        override fun toString(): String = "SignOut(sessions=$sessions)"
    }
}
