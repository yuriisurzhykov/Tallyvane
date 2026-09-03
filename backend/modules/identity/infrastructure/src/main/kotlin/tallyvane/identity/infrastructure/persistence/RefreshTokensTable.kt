package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.refresh_tokens` from `V20260902000000__identity_schema.sql`. [hash] is the
 * primary key — no separate surrogate id, per the migration's own comment: nothing looks a row up
 * by an identity of its own, and a row is immutable once inserted. [status] stores
 * [RefreshTokenStatus]'s own lowercase name, matching the migration's `check` constraint literals
 * — never an ordinal, and never a type this table's own module exposes past `infrastructure`.
 */
internal object RefreshTokensTable : Table("identity.refresh_tokens") {
    val hash = text("hash")
    val familyId = uuid("family_id")
    val sessionId = uuid("session_id").references(SessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val pepperVersion = integer("pepper_version")
    val status = text("status")
    val issuedAt = timestampWithTimeZone("issued_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()

    override val primaryKey = PrimaryKey(hash)
}
