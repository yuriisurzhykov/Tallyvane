package tallyvane.identity.domain.secondfactor

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/** One-use authority to perform one security action in one active session. */
public data class AuthenticationActionProof(
    public val token: HashedToken,
    public val userId: UserId,
    public val sessionId: SessionId,
    public val action: AuthenticationAction,
    public val policyVersion: Long,
    public val schemeId: String,
    public val assuranceRank: Int,
    public val expiresAt: Instant,
    public val consumedAt: Instant? = null,
) {
    init {
        require(action != AuthenticationAction.SIGN_IN) { "Sign-in does not use action proofs" }
        require(policyVersion > 0) { "Policy version must be positive" }
        require(schemeId.isNotBlank()) { "Scheme id must not be blank" }
        require(assuranceRank > 0) { "Assurance rank must be positive" }
    }
}
