package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

internal open class BackupCodeRowsTable(tableName: String, ownerColumn: String, ownerId: Column<kotlin.uuid.Uuid>) :
    Table(tableName) {
    val userId = uuid(ownerColumn).references(ownerId, onDelete = ReferenceOption.CASCADE)
    val hash = text("hash")
    override val primaryKey = PrimaryKey(userId, hash)
}
