package tallyvane.identity.application.port

import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId

/**
 * A [SessionStore] backed by an in-memory list, for a use case's test to inspect what was saved
 * without a real database.
 */
internal class SessionStoreFake : SessionStore {
    val saved: MutableList<Session> = mutableListOf()

    override suspend fun save(session: Session) {
        saved += session
    }

    override suspend fun find(id: SessionId): Session? = saved.find { it.id == id }

    override suspend fun revoke(id: SessionId) {
        saved.removeAll { it.id == id }
    }

    override suspend fun revokeAllFor(userId: UserId) {
        saved.removeAll { it.userId == userId }
    }

    override suspend fun listFor(userId: UserId): List<Session> = saved.filter { it.userId == userId }
}
