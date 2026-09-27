package tallyvane.identity.infrastructure.persistence

internal object AdminAuthenticationPolicyAuditTable : AuthenticationPolicyAuditRowsTable(
    "identity.admin_authentication_policy_audit",
    "admin_id",
    AdminsTable.id,
)
