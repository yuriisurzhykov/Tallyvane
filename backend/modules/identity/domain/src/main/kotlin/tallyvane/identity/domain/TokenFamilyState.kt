package tallyvane.identity.domain

/**
 * What storage found when a presented refresh token was looked up: which session it belongs to,
 * and whether it has already been consumed by an earlier rotation.
 *
 * Scoped to exactly what [RefreshRotationPolicy] needs to answer "has this token been used
 * before" — nothing about when the token was issued or when it expires. Whether a refresh token is
 * simply too old is a separate question, answered wherever this state is looked up from, not by
 * this policy: reuse is a security question (has someone presented a token twice), expiry is an
 * ordinary lifetime question, and the two do not share an answer.
 */
public data class TokenFamilyState(public val sessionId: SessionId, public val used: Boolean)
