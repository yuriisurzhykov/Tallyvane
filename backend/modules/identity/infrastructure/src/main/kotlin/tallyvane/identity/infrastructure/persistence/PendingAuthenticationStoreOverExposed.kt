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
internal class PendingAuthenticationStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    PendingAuthenticationStore {
    private val instant = InstantColumn()
    private val table: PendingAuthenticationRowsTable = when (realm) {
        IdentityRealm.USER -> PendingAuthenticationsTable
        IdentityRealm.ADMIN -> AdminPendingAuthenticationsTable
    }

    override suspend fun save(pending: PendingAuthentication) {
        table.insert {
            it[table.id] = pending.id.value
            it[table.userId] = pending.userId.value
            it[table.device] = pending.device.value
            it[table.recommendedMethod] = pending.recommendedMethod.name
            it[table.availableMethods] = pending.availableMethods.map { method -> method.name }
            it[table.createdAt] = instant.toColumn(pending.createdAt)
            it[table.expiresAt] = instant.toColumn(pending.expiresAt)
            it[table.policyVersion] = pending.policyVersion
        }
    }

    override suspend fun find(id: PendingAuthenticationId): PendingAuthentication? = table
        .selectAll()
        .where { table.id eq id.value }
        .singleOrNull()
        ?.toPendingAuthentication()

    override suspend fun delete(id: PendingAuthenticationId) {
        table.deleteWhere { table.id eq id.value }
    }

    override suspend fun deleteFor(userId: UserId) {
        table.deleteWhere { table.userId eq userId.value }
    }

    private fun ResultRow.toPendingAuthentication(): PendingAuthentication = PendingAuthentication(
        id = PendingAuthenticationId(this[table.id]),
        userId = UserId(this[table.userId]),
        device = DeviceLabel(this[table.device]),
        recommendedMethod = SecondFactorKind.valueOf(this[table.recommendedMethod]),
        availableMethods = this[table.availableMethods].map {
            SecondFactorKind.valueOf(it)
        }.toSet(),
        createdAt = instant.toDomain(this[table.createdAt]),
        expiresAt = instant.toDomain(this[table.expiresAt]),
        policyVersion = this[table.policyVersion],
    )
}
