package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.sessions`, including the short-lived nullable current-access-token fields.
 */
internal open class SessionRowsTable(tableName: String, ownerColumn: String, ownerId: Column<kotlin.uuid.Uuid>) :
    Table(tableName) {
    val id = uuid("id")
    val userId = uuid(ownerColumn).references(ownerId, onDelete = ReferenceOption.CASCADE)
    val device = text("device")
    val tokenFamilyId = uuid("token_family_id")
    val createdAt = timestampWithTimeZone("created_at")
    val lastUsedAt = timestampWithTimeZone("last_used_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val reauthenticatedAt = timestampWithTimeZone("reauthenticated_at").nullable()
    val currentAccessTokenHash = text("current_access_token_hash").nullable()
    val currentAccessTokenPepperVersion = integer("current_access_token_pepper_version").nullable()
    val currentAccessTokenExpiresAt = timestampWithTimeZone("current_access_token_expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
