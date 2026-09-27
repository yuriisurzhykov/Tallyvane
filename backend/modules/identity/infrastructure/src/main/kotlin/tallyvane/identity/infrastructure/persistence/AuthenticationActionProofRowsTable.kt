package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal open class AuthenticationActionProofRowsTable(
    tableName: String,
    ownerColumn: String,
    ownerId: Column<kotlin.uuid.Uuid>,
    sessions: SessionRowsTable,
) : Table(tableName) {
    val hash = text("hash")
    val pepperVersion = integer("pepper_version")
    val userId = uuid(ownerColumn).references(ownerId, onDelete = ReferenceOption.CASCADE)
    val sessionId = uuid("session_id").references(sessions.id, onDelete = ReferenceOption.CASCADE)
    val action = text("action")
    val policyVersion = long("policy_version")
    val schemeId = text("scheme_id")
    val assuranceRank = integer("assurance_rank")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()

    override val primaryKey = PrimaryKey(hash)
}
