package tallyvane.identity.infrastructure.persistence

/**
 * [RefreshTokensTable.status]'s three legal values, matching the migration's own `check`
 * constraint literals via [name] lowercased — never seen outside this module's repositories:
 * [tallyvane.identity.application.port.RefreshTokenStore]'s own vocabulary is
 * [tallyvane.identity.domain.token.TokenFamilyState.used] and
 * [tallyvane.identity.application.port.RefreshTokenStore.RotateOutcome], not this.
 */
internal enum class RefreshTokenStatus { ACTIVE, CONSUMED, REVOKED }
