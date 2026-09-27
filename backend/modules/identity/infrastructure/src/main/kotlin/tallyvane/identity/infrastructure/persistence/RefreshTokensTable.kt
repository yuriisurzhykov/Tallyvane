package tallyvane.identity.infrastructure.persistence

internal object RefreshTokensTable : RefreshTokenRowsTable("identity.refresh_tokens", SessionsTable)
