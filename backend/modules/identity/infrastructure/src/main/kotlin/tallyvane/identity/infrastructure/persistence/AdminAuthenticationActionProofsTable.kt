package tallyvane.identity.infrastructure.persistence

internal object AdminAuthenticationActionProofsTable : AuthenticationActionProofRowsTable(
    "identity.admin_authentication_action_proofs",
    "admin_id",
    AdminsTable.id,
    AdminSessionsTable,
)
