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
 * Signs every device out at once — every session [userId] has, this one included if its
 * [SessionId] is among them. A caller wanting "every device but mine" excludes its own session id
 * from the list this returns before calling anything further; this use case itself has no
 * "current session" concept to exclude by.
 */
public interface RevokeAllSessionsUseCase : UseCase {
    /**
     * @return Every [SessionId] this call revoked, so a caller can tell its own session apart
     * from the rest without a second lookup.
     */
    public suspend fun revokeAll(userId: UserId): List<SessionId>

    /**
     * One [transactions.inTransaction] covers the whole method, per
     * `SignInWithPasswordUseCase.SignIn`'s own KDoc and `backend/playground/transactions/README.md`.
     */
    public class RevokeAll internal constructor(
        private val sessions: SessionStore,
        private val refreshTokens: RefreshTokenStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : RevokeAllSessionsUseCase {
        override suspend fun revokeAll(userId: UserId): List<SessionId> = transactions.inTransaction {
            val ids = sessions.listFor(userId).filter { it.revokedAt == null }.map { it.id }
            sessions.revokeAllFor(userId, clock.now())
            ids.forEach { refreshTokens.revokeAllFor(it) }
            Verdict.Commit(ids)
        }
    }
}
