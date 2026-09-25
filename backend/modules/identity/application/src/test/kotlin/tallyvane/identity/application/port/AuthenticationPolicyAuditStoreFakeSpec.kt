package tallyvane.identity.application.port

class AuthenticationPolicyAuditStoreFakeSpec : AuthenticationPolicyAuditStoreConformance() {
    private val recorded = mutableListOf<RecordedPolicyChange>()

    override fun fresh(): AuthenticationPolicyAuditStore = MemoryAuditStore(recorded)

    override fun events(): List<RecordedPolicyChange> = recorded.toList()
}

private class MemoryAuditStore(private val events: MutableList<RecordedPolicyChange>) : AuthenticationPolicyAuditStore {
    override suspend fun record(
        actor: tallyvane.identity.domain.user.UserId,
        action: String,
        policyVersion: Long,
        occurredAt: kotlin.time.Instant,
    ) {
        events += RecordedPolicyChange(actor, action, policyVersion, occurredAt)
    }
}
