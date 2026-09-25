package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant
import kotlin.uuid.Uuid

public abstract class AuthenticationPolicyAuditStoreConformance : StringSpec() {
    protected abstract fun fresh(): AuthenticationPolicyAuditStore
    protected abstract fun events(): List<RecordedPolicyChange>

    init {
        "audit records preserve actor, action, policy version, and timestamp" {
            val actor = UserId(Uuid.random())
            val occurredAt = Instant.parse("2026-01-01T00:00:00Z")
            fresh().record(actor, "MFA_RESET", 4, occurredAt)
            events() shouldBe listOf(RecordedPolicyChange(actor, "MFA_RESET", 4, occurredAt))
        }
    }
}

public data class RecordedPolicyChange(
    val actor: UserId,
    val action: String,
    val policyVersion: Long,
    val occurredAt: Instant,
)
