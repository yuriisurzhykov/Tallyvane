package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.totp_enrollments` from `V20260902000000__identity_schema.sql`.
 */
internal object TotpEnrollmentsTable : Table("identity.totp_enrollments") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val encryptedSecret = text("encrypted_secret")
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId)
}
