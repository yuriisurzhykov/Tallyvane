package tallyvane.identity.infrastructure.persistence

internal object AdminSessionsTable : SessionRowsTable("identity.admin_sessions", "admin_id", AdminsTable.id)
