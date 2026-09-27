package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.refresh_tokens`; the token hash is the immutable primary key.
 */
internal open class RefreshTokenRowsTable(tableName: String, sessions: SessionRowsTable) : Table(tableName) {
    val hash = text("hash")
    val familyId = uuid("family_id")
    val sessionId = uuid("session_id").references(sessions.id, onDelete = ReferenceOption.CASCADE)
    val pepperVersion = integer("pepper_version")
    val status = text("status")
    val issuedAt = timestampWithTimeZone("issued_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()

    override val primaryKey = PrimaryKey(hash)
}
