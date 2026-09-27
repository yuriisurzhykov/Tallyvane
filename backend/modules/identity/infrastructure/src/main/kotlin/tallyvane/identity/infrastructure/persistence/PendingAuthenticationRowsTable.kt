package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.pending_authentications`; methods are persisted by name, never by ordinal.
 */
internal open class PendingAuthenticationRowsTable(
    tableName: String,
    ownerColumn: String,
    ownerId: Column<kotlin.uuid.Uuid>,
) : Table(tableName) {
    val id = uuid("id")
    val userId = uuid(ownerColumn).references(ownerId, onDelete = ReferenceOption.CASCADE)
    val device = text("device")
    val recommendedMethod = text("recommended_method")
    val availableMethods = array("available_methods", TextColumnType())
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val policyVersion = long("policy_version").default(1)

    override val primaryKey = PrimaryKey(id)
}
