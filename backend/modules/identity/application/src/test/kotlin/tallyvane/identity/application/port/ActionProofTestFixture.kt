package tallyvane.identity.application.port

import tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal val VALID_ACTION_PROOF = "actionproof_${"A".repeat(43)}"

internal fun acceptingActionProofRequirement(
    userId: UserId,
    sessionId: SessionId,
    now: Instant,
): AuthenticationActionProofRequirement {
    val sessions = SessionStoreFake().also {
        it.saved[sessionId] = Session(
            sessionId,
            userId,
            DeviceLabel("Browser"),
            TokenFamilyId(Uuid.random()),
            now,
            now,
            null,
        )
    }
    val proofs = object : AuthenticationActionProofStore {
        override suspend fun save(proof: AuthenticationActionProof) = Unit
        override suspend fun consume(
            token: HashedToken,
            userId: UserId,
            sessionId: SessionId,
            action: AuthenticationAction,
            policyVersion: Long,
            now: Instant,
        ) = true
        override suspend fun deleteAllFor(userId: UserId) = Unit
    }
    return AuthenticationActionProofRequirement(
        proofs,
        AuthenticationPolicyStoreFake(),
        sessions,
        TokenHasherFake(),
        ClockFake(now),
    )
}
