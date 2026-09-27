package tallyvane.identity.infrastructure.persistence

internal object AdminRefreshTokensTable : RefreshTokenRowsTable("identity.admin_refresh_tokens", AdminSessionsTable)
