package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal object AuthenticationPolicyAuditTable : Table("identity.authentication_policy_audit") {
    val id = long("id").autoIncrement()
    val actorUserId = uuid("actor_user_id")
    val action = text("action")
    val policyVersion = long("policy_version")
    val occurredAt = timestampWithTimeZone("occurred_at")

    override val primaryKey = PrimaryKey(id)
}
