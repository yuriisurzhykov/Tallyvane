package tallyvane.identity.application.session

import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Signs one device out — the `DELETE /api/v1/auth/sessions/{id}` action, called by the account
 * holder themselves against one of their own sessions, per `GET /api/v1/auth/sessions`'s own list.
 */
public interface RevokeSessionUseCase : UseCase {
    /**
     * @param userId The caller's own id, already resolved from their current session by
     * whichever route calls this — never taken from the request body, so one account can never
     * revoke another's session by guessing its id.
     */
    public suspend fun revoke(userId: UserId, sessionId: SessionId): RevokeSessionOutcome

    /**
     * One [transactions.inTransaction] covers the whole method, per
     * `SignInWithPasswordUseCase.SignIn`'s own KDoc and `backend/playground/transactions/README.md`.
     */
    public class Revoke internal constructor(
        private val sessions: SessionStore,
        private val refreshTokens: RefreshTokenStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : RevokeSessionUseCase {
        override suspend fun revoke(userId: UserId, sessionId: SessionId): RevokeSessionOutcome =
            transactions.inTransaction {
                val session = sessions.find(sessionId)
                val outcome = if (session == null || session.userId != userId) {
                    RevokeSessionOutcome.NotFound
                } else {
                    val now = clock.now()
                    sessions.revoke(sessionId, now)
                    refreshTokens.revokeAllFor(sessionId)
                    RevokeSessionOutcome.Revoked
                }
                Verdict.Commit(outcome)
            }
    }
}
