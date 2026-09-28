package tallyvane.identity.application.port

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenFamilyState
import kotlin.time.Instant

/**
 * The RFC 9700 §4.14.2 rotation-with-reuse-detection ledger: every refresh token this module has
 * ever minted, active or not, grouped by [TokenFamilyId] — the whole lineage descending from one
 * sign-in.
 *
 * Never opens a transaction of its own, matching [SessionStore]'s own rule: whichever use case
 * calls this is the one that decided where its transaction begins.
 */
public interface RefreshTokenStore {
    /**
     * The first refresh token for a brand-new [TokenFamilyId] — [tallyvane.identity.application.SessionIssuer]'s
     * own call, once per session.
     *
     * @param issuedAt Supplied by the caller's own [tallyvane.platform.kernel.Clock] — this store
     * never reads a wall clock of its own, the same rule [SessionStore.revoke]'s [Instant] states.
     */
    public suspend fun issueFirst(
        sessionId: SessionId,
        familyId: TokenFamilyId,
        hash: HashedToken,
        expiresAt: Instant,
        issuedAt: Instant,
    )

    /**
     * What is known about a presented refresh token's [hash] — `null` if this module never minted
     * one with this hash at all. [TokenFamilyState.used] is what
     * [tallyvane.identity.domain.token.RefreshRotationPolicy] decides `Rotate` or `ReuseDetected`
     * from.
     */
    public suspend fun stateOf(hash: HashedToken): TokenFamilyState?

    /**
     * Marks [oldHash]'s row consumed and inserts [newHash] as a new active row in the same
     * family, atomically — only if [oldHash] was still active. [RotateOutcome.AlreadyRotated]
     * means a concurrent request rotated it first, the same race
     * [tallyvane.identity.application.port.UserRepository.insert] already names for a duplicate
     * email: decided by the write itself, not a check beforehand.
     *
     * @param now Supplied by the caller's own [tallyvane.platform.kernel.Clock] — stamps both the
     * old row's `consumed_at` and the new row's `issued_at`, the one instant this rotation happens
     * at.
     */
    public suspend fun rotate(
        oldHash: HashedToken,
        newHash: HashedToken,
        expiresAt: Instant,
        now: Instant,
    ): RotateOutcome

    /**
     * Marks every still-active row for [sessionId] revoked — called once a
     * [tallyvane.identity.domain.token.RefreshRotationDecision.ReuseDetected] or an explicit
     * sign-out reaches this store.
     */
    public suspend fun revokeAllFor(sessionId: SessionId)

    /**
     * Deletes every row whose `issued_at` is older than [cutoff] — active, consumed or revoked
     * alike, since [tallyvane.identity.domain.token.RefreshTokenRetentionPolicy] names an age past
     * which none of the three still matters. [cutoff] is computed by that policy, never a raw
     * duration this store would have to subtract from its own clock.
     *
     * @return How many rows were actually deleted, so a caller can log the sweep's own size.
     */
    public suspend fun deleteIssuedBefore(cutoff: Instant): Int

    /**
     * [Rotated] carries [sessionId] rather than making the caller ask [stateOf] a second time for
     * a fact this store's own write already knew — the same reasoning
     * [tallyvane.identity.application.SessionIssuer.IssuedSession] carries a [TokenPair] instead
     * of making its caller re-derive one.
     */
    public sealed interface RotateOutcome {
        public data class Rotated(public val sessionId: SessionId) : RotateOutcome

        public data object AlreadyRotated : RotateOutcome
    }
}
