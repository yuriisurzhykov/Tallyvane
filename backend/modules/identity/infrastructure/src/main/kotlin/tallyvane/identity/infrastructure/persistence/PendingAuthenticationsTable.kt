package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.pending_authentications` from `V20260902000000__identity_schema.sql`.
 * [availableMethods] stores each [tallyvane.identity.domain.secondfactor.SecondFactorKind]'s own
 * `name` — the repository maps the set to and from that array, never storing an ordinal.
 */
internal object PendingAuthenticationsTable : Table("identity.pending_authentications") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val device = text("device")
    val availableMethods = array("available_methods", TextColumnType())
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val requiresEnrollment = bool("requires_enrollment").default(false)
    val primaryMethod = text("primary_method").nullable()
    val policyVersion = long("policy_version").default(1)

    override val primaryKey = PrimaryKey(id)
}
