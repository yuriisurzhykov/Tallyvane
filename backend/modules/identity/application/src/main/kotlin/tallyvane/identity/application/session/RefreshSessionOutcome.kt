package tallyvane.identity.application.session

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenPair

/**
 * What came of presenting a refresh token to [RefreshSessionUseCase].
 */
public sealed interface RefreshSessionOutcome {
    /**
     * The presented token was still active — a new [TokenPair] was minted, and the old refresh
     * token can never be redeemed again.
     */
    public data class Issued(public val sessionId: SessionId, public val tokens: TokenPair) : RefreshSessionOutcome

    /**
     * The presented token names no session this module ever issued one for, has already been
     * consumed by an earlier rotation, or lost the race to a concurrent request rotating the
     * same token — the caller should sign in again, exactly as for an unknown token, since this
     * store cannot distinguish "never existed" from "already spent" without leaking which one it
     * is to whoever presented it.
     */
    public data object Invalid : RefreshSessionOutcome

    /**
     * The presented token had already been consumed by an earlier rotation before this one — the
     * sign a refresh token may have been stolen, per RFC 9700 §4.14.2. The session named by
     * [tallyvane.identity.domain.token.RefreshRotationDecision.ReuseDetected] has already been
     * revoked by the time this is returned.
     */
    public data object ReuseDetected : RefreshSessionOutcome
}
