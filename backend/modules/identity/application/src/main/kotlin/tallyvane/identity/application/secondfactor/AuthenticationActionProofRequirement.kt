package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenValue
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock

/** Consumes one action proof inside the caller's mutation transaction. */
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
    ): Boolean {
        if (rawProof.isNullOrBlank()) return false
        val token = runCatching { TokenValue(rawProof) }.getOrNull() ?: return false
        if (!rawProof.startsWith("actionproof_")) return false
        val session = sessions.find(sessionId)
        if (session?.userId != userId || session.revokedAt != null) return false
        val policy = policies.current() ?: AuthenticationPolicy.defaults()
        return proofs.consume(hasher.hash(token), userId, sessionId, action, policy.version, clock.now())
    }
}
