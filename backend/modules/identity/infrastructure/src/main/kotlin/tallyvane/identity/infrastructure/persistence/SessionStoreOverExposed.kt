package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * [SessionStore] over [SessionsTable], for a real Postgres. Opens no transaction of its own — see
 * that port's own KDoc for why.
 */
internal class SessionStoreOverExposed : SessionStore {
    private val instant = InstantColumn()

    override suspend fun save(session: Session) {
        SessionsTable.insert {
            it[id] = session.id.value
            it[userId] = session.userId.value
            it[device] = session.device.value
            it[tokenFamilyId] = session.tokenFamilyId.value
            it[createdAt] = instant.toColumn(session.createdAt)
            it[lastUsedAt] = instant.toColumn(session.lastUsedAt)
            it[revokedAt] = session.revokedAt?.let(instant::toColumn)
        }
    }

    override suspend fun find(id: SessionId): Session? =
        SessionsTable.selectAll().where { SessionsTable.id eq id.value }.singleOrNull()?.toSession()

    override suspend fun revoke(id: SessionId, revokedAt: Instant) {
        SessionsTable.update({ SessionsTable.id eq id.value }) {
            it[SessionsTable.revokedAt] = instant.toColumn(revokedAt)
        }
    }

    override suspend fun revokeAllFor(userId: UserId, revokedAt: Instant) {
        SessionsTable.update({ SessionsTable.userId eq userId.value }) {
            it[SessionsTable.revokedAt] = instant.toColumn(revokedAt)
        }
    }

    override suspend fun listFor(userId: UserId): List<Session> =
        SessionsTable.selectAll().where { SessionsTable.userId eq userId.value }.map { it.toSession() }

    override suspend fun attachAccessToken(id: SessionId, hash: HashedToken, expiresAt: Instant, lastUsedAt: Instant) {
        SessionsTable.update({ SessionsTable.id eq id.value }) {
            it[currentAccessTokenHash] = hash.hash.revealed()
            it[currentAccessTokenPepperVersion] = hash.pepperVersion
            it[currentAccessTokenExpiresAt] = instant.toColumn(expiresAt)
            it[SessionsTable.lastUsedAt] = instant.toColumn(lastUsedAt)
        }
    }

    override suspend fun findByAccessTokenHash(hash: HashedToken, now: Instant): Session? {
        val row = SessionsTable
            .selectAll()
            .where { SessionsTable.currentAccessTokenHash eq hash.hash.revealed() }
            .singleOrNull()
        val expiresAt = row?.get(SessionsTable.currentAccessTokenExpiresAt)?.let(instant::toDomain)
        return row?.toSession()?.takeIf { expiresAt != null && it.revokedAt == null && expiresAt > now }
    }

    private fun ResultRow.toSession(): Session = Session(
        id = SessionId(this[SessionsTable.id]),
        userId = UserId(this[SessionsTable.userId]),
        device = DeviceLabel(this[SessionsTable.device]),
        tokenFamilyId = TokenFamilyId(this[SessionsTable.tokenFamilyId]),
        createdAt = instant.toDomain(this[SessionsTable.createdAt]),
        lastUsedAt = instant.toDomain(this[SessionsTable.lastUsedAt]),
        revokedAt = this[SessionsTable.revokedAt]?.let(instant::toDomain),
    )
}
