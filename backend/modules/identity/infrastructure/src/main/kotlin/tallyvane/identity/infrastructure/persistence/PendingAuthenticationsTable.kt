package tallyvane.identity.infrastructure.persistence

internal object PendingAuthenticationsTable :
    PendingAuthenticationRowsTable("identity.pending_authentications", "user_id", UsersTable.id)
