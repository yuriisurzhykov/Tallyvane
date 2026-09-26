package tallyvane.identity.application.port

import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * Security-relevant policy changes and refused writes; never contains credentials or codes.
 */
public interface AuthenticationPolicyAuditStore {
    public suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant)
}
