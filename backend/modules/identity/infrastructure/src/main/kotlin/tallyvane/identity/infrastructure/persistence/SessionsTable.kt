package tallyvane.identity.infrastructure.persistence

internal object SessionsTable : SessionRowsTable("identity.sessions", "user_id", UsersTable.id)
