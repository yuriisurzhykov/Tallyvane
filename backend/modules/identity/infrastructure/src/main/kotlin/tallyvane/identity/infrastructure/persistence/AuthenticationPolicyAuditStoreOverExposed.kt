package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * Writes security audit events to PostgreSQL in the caller's transaction.
 */
internal class AuthenticationPolicyAuditStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    AuthenticationPolicyAuditStore {
    private val instant = InstantColumn()
    private val table: AuthenticationPolicyAuditRowsTable = when (realm) {
        IdentityRealm.USER -> AuthenticationPolicyAuditTable
        IdentityRealm.ADMIN -> AdminAuthenticationPolicyAuditTable
    }

    override suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant) {
        table.insert {
            it[table.actorUserId] = actor.value
            it[table.action] = action
            it[table.policyVersion] = policyVersion
            it[table.occurredAt] = instant.toColumn(occurredAt)
        }
    }
}
