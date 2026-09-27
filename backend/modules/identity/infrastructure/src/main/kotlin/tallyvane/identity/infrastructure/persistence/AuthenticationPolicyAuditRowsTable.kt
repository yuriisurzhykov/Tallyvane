package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal open class AuthenticationPolicyAuditRowsTable(
    tableName: String,
    actorColumn: String,
    actorId: Column<kotlin.uuid.Uuid>,
) : Table(tableName) {
    val id = long("id").autoIncrement()
    val actorUserId = uuid(actorColumn).references(actorId, onDelete = ReferenceOption.CASCADE)
    val action = text("action")
    val policyVersion = long("policy_version")
    val occurredAt = timestampWithTimeZone("occurred_at")

    override val primaryKey = PrimaryKey(id)
}
