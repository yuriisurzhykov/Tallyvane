package tallyvane.identity.infrastructure.persistence

internal object AdminPendingAuthenticationsTable :
    PendingAuthenticationRowsTable("identity.admin_pending_authentications", "admin_id", AdminsTable.id)
