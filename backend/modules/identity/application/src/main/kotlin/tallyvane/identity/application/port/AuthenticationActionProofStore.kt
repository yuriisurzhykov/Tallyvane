package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.domain.session.SessionId
import kotlin.time.Instant

/** Persistence boundary for short-lived, action-scoped authorization proofs. */
public interface AuthenticationActionProofStore {
    public suspend fun save(proof: AuthenticationActionProof)

    /** Atomically consumes a valid proof matching the full action context. */
    public suspend fun consume(
        token: HashedToken,
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
        policyVersion: Long,
        now: Instant,
    ): Boolean

    public suspend fun deleteAllFor(userId: UserId)
}
