package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * Writes security audit events to PostgreSQL in the caller's transaction.
 */
internal class AuthenticationPolicyAuditStoreOverExposed : AuthenticationPolicyAuditStore {
    private val instant = InstantColumn()

    override suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant) {
        AuthenticationPolicyAuditTable.insert {
            it[actorUserId] = actor.value
            it[AuthenticationPolicyAuditTable.action] = action
            it[AuthenticationPolicyAuditTable.policyVersion] = policyVersion
            it[AuthenticationPolicyAuditTable.occurredAt] = instant.toColumn(occurredAt)
        }
    }
}
