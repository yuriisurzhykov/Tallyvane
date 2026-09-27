package tallyvane.identity.infrastructure.persistence

internal object AuthenticationPolicyAuditTable : AuthenticationPolicyAuditRowsTable(
    "identity.authentication_policy_audit",
    "actor_user_id",
    UsersTable.id,
)
