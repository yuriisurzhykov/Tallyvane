package tallyvane.identity.application.session

import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * The account holder's own "connected devices" list — the `GET /api/v1/auth/sessions` action.
 * Includes a session already revoked, so a caller can show "you signed this device out" instead
 * of it silently disappearing.
 */
public interface ListSessionsUseCase : UseCase {
    public suspend fun list(userId: UserId): List<Session>

    /**
     * Named `ListSessions`, not the bare verb every other nested implementation uses (`SignIn`,
     * `Verify`, `Enroll`, `Refresh`): `List` would shadow `kotlin.collections.List`, and `Default`
     * is not distinct enough to safely name a test file after — a second use case that also
     * picked `Default` would collide with this one's `DefaultSpec`.
     */
    public class ListSessions internal constructor(
        private val sessions: SessionStore,
        private val transactions: TransactionRunner,
    ) : ListSessionsUseCase {
        override suspend fun list(userId: UserId): List<Session> = transactions.inTransaction {
            Verdict.Commit(sessions.listFor(userId))
        }
    }
}
