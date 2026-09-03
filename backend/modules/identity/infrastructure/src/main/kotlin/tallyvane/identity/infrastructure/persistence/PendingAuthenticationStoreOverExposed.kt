package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId

/**
 * [PendingAuthenticationStore] over [PendingAuthenticationsTable], for a real Postgres. Opens no
 * transaction of its own — see that port's own KDoc for why.
 */
internal class PendingAuthenticationStoreOverExposed : PendingAuthenticationStore {
    private val instant = InstantColumn()

    override suspend fun save(pending: PendingAuthentication) {
        PendingAuthenticationsTable.insert {
            it[id] = pending.id.value
            it[userId] = pending.userId.value
            it[device] = pending.device.value
            it[availableMethods] = pending.availableMethods.map { method -> method.name }
            it[createdAt] = instant.toColumn(pending.createdAt)
            it[expiresAt] = instant.toColumn(pending.expiresAt)
        }
    }

    override suspend fun find(id: PendingAuthenticationId): PendingAuthentication? = PendingAuthenticationsTable
        .selectAll()
        .where { PendingAuthenticationsTable.id eq id.value }
        .singleOrNull()
        ?.toPendingAuthentication()

    override suspend fun delete(id: PendingAuthenticationId) {
        PendingAuthenticationsTable.deleteWhere { PendingAuthenticationsTable.id eq id.value }
    }

    private fun ResultRow.toPendingAuthentication(): PendingAuthentication = PendingAuthentication(
        id = PendingAuthenticationId(this[PendingAuthenticationsTable.id]),
        userId = UserId(this[PendingAuthenticationsTable.userId]),
        device = DeviceLabel(this[PendingAuthenticationsTable.device]),
        availableMethods = this[PendingAuthenticationsTable.availableMethods].map {
            SecondFactorKind.valueOf(it)
        }.toSet(),
        createdAt = instant.toDomain(this[PendingAuthenticationsTable.createdAt]),
        expiresAt = instant.toDomain(this[PendingAuthenticationsTable.expiresAt]),
    )
}
