package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

internal object BackupCodesTable : Table("identity.backup_codes") {
    val userId = uuid("user_id")
    val hash = text("hash")
    override val primaryKey = PrimaryKey(userId, hash)
}
