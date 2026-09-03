package tallyvane.identity.application.port

import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * A [SessionStore] backed by an in-memory map, for a use case's test to inspect what was saved
 * without a real database.
 */
internal class SessionStoreFake : SessionStore {
    val saved: MutableMap<SessionId, Session> = mutableMapOf()
    private val accessTokens: MutableMap<SessionId, Pair<HashedToken, Instant>> = mutableMapOf()

    override suspend fun save(session: Session) {
        saved[session.id] = session
    }

    override suspend fun find(id: SessionId): Session? = saved[id]

    override suspend fun revoke(id: SessionId, revokedAt: Instant) {
        saved[id]?.let { session -> saved[id] = session.copy(revokedAt = revokedAt) }
    }

    override suspend fun revokeAllFor(userId: UserId, revokedAt: Instant) {
        saved.replaceAll { _, session ->
            if (session.userId == userId) session.copy(revokedAt = revokedAt) else session
        }
    }

    override suspend fun listFor(userId: UserId): List<Session> = saved.values.filter { it.userId == userId }

    override suspend fun attachAccessToken(id: SessionId, hash: HashedToken, expiresAt: Instant, lastUsedAt: Instant) {
        accessTokens[id] = hash to expiresAt
        saved[id]?.let { session -> saved[id] = session.copy(lastUsedAt = lastUsedAt) }
    }

    override suspend fun findByAccessTokenHash(hash: HashedToken, now: Instant): Session? {
        val match = accessTokens.entries.find { it.value.first == hash }
        val session = match?.let { saved[it.key] }
        val expiresAt = match?.value?.second
        return session?.takeIf { it.revokedAt == null && expiresAt != null && expiresAt > now }
    }
}
