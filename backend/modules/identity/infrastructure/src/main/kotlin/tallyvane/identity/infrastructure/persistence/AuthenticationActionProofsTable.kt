package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal object AuthenticationActionProofsTable : Table("identity.authentication_action_proofs") {
    val hash = text("hash")
    val pepperVersion = integer("pepper_version")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val sessionId = uuid("session_id").references(SessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val action = text("action")
    val policyVersion = long("policy_version")
    val schemeId = text("scheme_id")
    val assuranceRank = integer("assurance_rank")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()

    override val primaryKey = PrimaryKey(hash)
}
