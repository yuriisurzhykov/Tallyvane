package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
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
internal class SessionStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) : SessionStore {
    private val instant = InstantColumn()
    private val table: SessionRowsTable = when (realm) {
        IdentityRealm.USER -> SessionsTable
        IdentityRealm.ADMIN -> AdminSessionsTable
    }

    override suspend fun save(session: Session) {
        table.insert {
            it[id] = session.id.value
            it[userId] = session.userId.value
            it[device] = session.device.value
            it[tokenFamilyId] = session.tokenFamilyId.value
            it[createdAt] = instant.toColumn(session.createdAt)
            it[lastUsedAt] = instant.toColumn(session.lastUsedAt)
            it[revokedAt] = session.revokedAt?.let(instant::toColumn)
            it[reauthenticatedAt] = session.reauthenticatedAt?.let(instant::toColumn)
        }
    }

    override suspend fun find(id: SessionId): Session? =
        table.selectAll().where { table.id eq id.value }.singleOrNull()?.toSession()

    override suspend fun revoke(id: SessionId, revokedAt: Instant) {
        table.update({ table.id eq id.value }) {
            it[table.revokedAt] = instant.toColumn(revokedAt)
        }
    }

    override suspend fun revokeAllFor(userId: UserId, revokedAt: Instant) {
        table.update({ table.userId eq userId.value }) {
            it[table.revokedAt] = instant.toColumn(revokedAt)
        }
    }

    override suspend fun listFor(userId: UserId): List<Session> =
        table.selectAll().where { table.userId eq userId.value }.map { it.toSession() }

    override suspend fun recordReauthentication(id: SessionId, userId: UserId, at: Instant): Boolean = table.update({
        (table.id eq id.value) and (table.userId eq userId.value) and
            table.revokedAt.isNull()
    }) {
        it[table.reauthenticatedAt] = instant.toColumn(at)
    } == 1

    override suspend fun attachAccessToken(id: SessionId, hash: HashedToken, expiresAt: Instant, lastUsedAt: Instant) {
        table.update({ table.id eq id.value }) {
            it[table.currentAccessTokenHash] = hash.hash.revealed()
            it[table.currentAccessTokenPepperVersion] = hash.pepperVersion
            it[table.currentAccessTokenExpiresAt] = instant.toColumn(expiresAt)
            it[table.lastUsedAt] = instant.toColumn(lastUsedAt)
        }
    }

    override suspend fun findByAccessTokenHash(hash: HashedToken, now: Instant): Session? {
        val row = table
            .selectAll()
            .where { table.currentAccessTokenHash eq hash.hash.revealed() }
            .singleOrNull()
        val expiresAt = row?.get(table.currentAccessTokenExpiresAt)?.let(instant::toDomain)
        return row?.toSession()?.takeIf { expiresAt != null && it.revokedAt == null && expiresAt > now }
    }

    private fun ResultRow.toSession(): Session = Session(
        id = SessionId(this[table.id]),
        userId = UserId(this[table.userId]),
        device = DeviceLabel(this[table.device]),
        tokenFamilyId = TokenFamilyId(this[table.tokenFamilyId]),
        createdAt = instant.toDomain(this[table.createdAt]),
        lastUsedAt = instant.toDomain(this[table.lastUsedAt]),
        revokedAt = this[table.revokedAt]?.let(instant::toDomain),
        reauthenticatedAt = this[table.reauthenticatedAt]?.let(instant::toDomain),
    )
}
