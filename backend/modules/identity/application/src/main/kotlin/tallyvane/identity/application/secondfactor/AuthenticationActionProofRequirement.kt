package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenValue
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock

/**
 * Consumes one action proof inside the caller's mutation transaction.
 */
public class AuthenticationActionProofRequirement internal constructor(
    private val proofs: AuthenticationActionProofStore,
    private val policies: AuthenticationPolicyStore,
    private val sessions: SessionStore,
    private val hasher: TokenHasher,
    private val clock: Clock,
) {
    public suspend fun consume(
        rawProof: String?,
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
    ): Boolean = parse(rawProof)?.let { token ->
        val activeSession = sessions.find(sessionId)?.let { it.userId == userId && it.revokedAt == null } == true
        val policyVersion = if (activeSession) policies.current()?.version else null
        policyVersion != null &&
            proofs.consume(hasher.hash(token), userId, sessionId, action, policyVersion, clock.now())
    } == true

    private fun parse(rawProof: String?): TokenValue? = rawProof
        ?.takeIf { it.isNotBlank() && it.startsWith("actionproof_") }
        ?.let { runCatching { TokenValue(it) }.getOrNull() }
}
