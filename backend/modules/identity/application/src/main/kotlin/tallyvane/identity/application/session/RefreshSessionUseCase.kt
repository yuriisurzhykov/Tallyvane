package tallyvane.identity.application.session

import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.RefreshRotationDecision
import tallyvane.identity.domain.token.RefreshRotationPolicy
import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.token.TokenPair
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration

/**
 * Redeems a presented refresh token for a fresh [TokenPair] — the RFC 9700 §4.14.2 rotation this
 * module's whole `refresh_tokens` ledger exists for: every redemption invalidates the token that
 * was just spent, so the same value presented twice is detectable as reuse rather than an
 * ordinary retry.
 */
public interface RefreshSessionUseCase {
    public suspend fun refresh(presented: TokenValue): RefreshSessionOutcome

    /**
     * One [transactions.inTransaction] covers the whole method — the lookup, the decision and
     * whichever write follows it — because [refreshTokens] and [sessions] open no transaction of
     * their own. Verified for real, not merely argued:
     * `backend/playground/transactions/README.md`'s 2026-09-02 entry.
     *
     * [RefreshTokenStore.RotateOutcome.ALREADY_ROTATED] is reported as [RefreshSessionOutcome.Invalid],
     * not [RefreshSessionOutcome.ReuseDetected]: it means a concurrent request redeemed the same
     * token first, inside the same narrow window this transaction's own read and write span — an
     * ordinary race between two requests from the same legitimate client (a doubled network
     * retry, a duplicated tab), not the sign of a stolen token
     * [tallyvane.identity.domain.token.RefreshRotationPolicy] exists to catch. That policy's own
     * [RefreshRotationDecision.ReuseDetected] is the one path that revokes a session here.
     */
    public class Refresh internal constructor(
        private val refreshTokens: RefreshTokenStore,
        private val sessions: SessionStore,
        private val policy: RefreshRotationPolicy,
        private val tokenFactory: TokenFactory,
        private val tokenHasher: TokenHasher,
        private val clock: Clock,
        private val transactions: TransactionRunner,
        private val accessTokenTtl: Duration,
        private val refreshTokenIdleTtl: Duration,
    ) : RefreshSessionUseCase {
        override suspend fun refresh(presented: TokenValue): RefreshSessionOutcome = transactions.inTransaction {
            val presentedHash = tokenHasher.hash(presented)
            val state = refreshTokens.stateOf(presentedHash)
            val outcome = if (state == null) {
                RefreshSessionOutcome.Invalid
            } else {
                when (val decision = policy.decide(state)) {
                    RefreshRotationDecision.Rotate -> rotate(presentedHash)
                    is RefreshRotationDecision.ReuseDetected -> reuseDetected(decision)
                }
            }
            Verdict.Commit(outcome)
        }

        private suspend fun rotate(presentedHash: HashedToken): RefreshSessionOutcome {
            val now = clock.now()
            val newRefresh = tokenFactory.mint(TokenKind.REFRESH)
            val rotated = refreshTokens.rotate(
                oldHash = presentedHash,
                newHash = tokenHasher.hash(newRefresh),
                expiresAt = now + refreshTokenIdleTtl,
                now = now,
            )
            return when (rotated) {
                RefreshTokenStore.RotateOutcome.AlreadyRotated -> RefreshSessionOutcome.Invalid
                is RefreshTokenStore.RotateOutcome.Rotated -> {
                    val newAccess = tokenFactory.mint(TokenKind.ACCESS)
                    sessions.attachAccessToken(
                        rotated.sessionId,
                        tokenHasher.hash(newAccess),
                        now + accessTokenTtl,
                        lastUsedAt = now,
                    )
                    RefreshSessionOutcome.Issued(rotated.sessionId, TokenPair(newAccess, newRefresh))
                }
            }
        }

        private suspend fun reuseDetected(decision: RefreshRotationDecision.ReuseDetected): RefreshSessionOutcome {
            refreshTokens.revokeAllFor(decision.sessionId)
            sessions.revoke(decision.sessionId, clock.now())
            return RefreshSessionOutcome.ReuseDetected
        }
    }
}
