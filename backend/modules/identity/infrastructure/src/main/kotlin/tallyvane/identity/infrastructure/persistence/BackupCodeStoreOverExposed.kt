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
internal class BackupCodeStoreOverExposed : BackupCodeStore {
    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        lockOwner(userId)
        BackupCodesTable.deleteWhere { BackupCodesTable.userId eq userId.value }
        hashes.forEach { hash ->
            BackupCodesTable.insert {
                it[BackupCodesTable.userId] = userId.value
                it[BackupCodesTable.hash] = hash.revealed()
            }
        }
    }

    override suspend fun consume(userId: UserId, hash: Secret): Boolean {
        lockOwner(userId)
        return BackupCodesTable.deleteWhere {
            (BackupCodesTable.userId eq userId.value) and (BackupCodesTable.hash eq hash.revealed())
        } == 1
    }

    override suspend fun hasAny(userId: UserId): Boolean =
        BackupCodesTable.selectAll().where { BackupCodesTable.userId eq userId.value }.limit(1).singleOrNull() != null

    private fun lockOwner(userId: UserId) {
        check(UsersTable.selectAll().where { UsersTable.id eq userId.value }.forUpdate().singleOrNull() != null)
    }
}
