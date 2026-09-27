package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.totp_enrollments`.
 */
internal open class TotpEnrollmentRowsTable(
    tableName: String,
    ownerColumn: String,
    ownerId: Column<kotlin.uuid.Uuid>,
) : Table(tableName) {
    val userId = uuid(ownerColumn).references(ownerId, onDelete = ReferenceOption.CASCADE)
    val encryptedSecret = text("encrypted_secret")
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId)
}
