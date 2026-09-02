package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.sessions` from `V20260902000000__identity_schema.sql` — see that migration
 * for why `currentAccessToken*` is nullable and how briefly.
 */
internal object SessionsTable : Table("identity.sessions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val device = text("device")
    val tokenFamilyId = uuid("token_family_id")
    val createdAt = timestampWithTimeZone("created_at")
    val lastUsedAt = timestampWithTimeZone("last_used_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val currentAccessTokenHash = text("current_access_token_hash").nullable()
    val currentAccessTokenPepperVersion = integer("current_access_token_pepper_version").nullable()
    val currentAccessTokenExpiresAt = timestampWithTimeZone("current_access_token_expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
