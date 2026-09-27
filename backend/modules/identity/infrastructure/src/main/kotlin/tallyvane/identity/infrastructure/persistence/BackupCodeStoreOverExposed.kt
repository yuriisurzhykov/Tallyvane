package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret

/**
 * Lock the owner row even for an empty code set, so concurrent reissues never merge generations.
 */
internal class BackupCodeStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) : BackupCodeStore {
    private val table: BackupCodeRowsTable = when (realm) {
        IdentityRealm.USER -> BackupCodesTable
        IdentityRealm.ADMIN -> AdminBackupCodesTable
    }

    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        lockOwner(userId)
        table.deleteWhere { table.userId eq userId.value }
        hashes.forEach { hash ->
            table.insert {
                it[table.userId] = userId.value
                it[table.hash] = hash.revealed()
            }
        }
    }

    override suspend fun consume(userId: UserId, hash: Secret): Boolean {
        lockOwner(userId)
        return table.deleteWhere {
            (table.userId eq userId.value) and (table.hash eq hash.revealed())
        } == 1
    }

    override suspend fun hasAny(userId: UserId): Boolean =
        table.selectAll().where { table.userId eq userId.value }.limit(1).singleOrNull() != null

    private fun lockOwner(userId: UserId) {
        val exists = when (realm) {
            IdentityRealm.USER -> UsersTable.selectAll().where { UsersTable.id eq userId.value }
            IdentityRealm.ADMIN -> AdminsTable.selectAll().where { AdminsTable.id eq userId.value }
        }.forUpdate().singleOrNull() != null
        check(exists)
    }
}
