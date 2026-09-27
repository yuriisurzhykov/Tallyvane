package tallyvane.identity.infrastructure.persistence

internal object AuthenticationActionProofsTable : AuthenticationActionProofRowsTable(
    "identity.authentication_action_proofs",
    "user_id",
    UsersTable.id,
    SessionsTable,
)
