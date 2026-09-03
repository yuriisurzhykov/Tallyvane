package tallyvane.identity.domain.token

import tallyvane.identity.domain.session.SessionId

/**
 * What storage found when a presented refresh token was looked up: which session it belongs to,
 * and whether it has already been consumed by an earlier rotation.
 *
 * Deliberately excludes expiry: whether a token is simply too old is a separate, ordinary
 * lifetime question answered wherever this state is looked up from, not a security question this
 * type answers.
 */
public data class TokenFamilyState(public val sessionId: SessionId, public val used: Boolean)
