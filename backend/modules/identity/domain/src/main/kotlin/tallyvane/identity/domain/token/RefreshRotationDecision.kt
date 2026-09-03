package tallyvane.identity.domain.token

import tallyvane.identity.domain.session.SessionId

/**
 * What [RefreshRotationPolicy] decided about one presented refresh token.
 *
 * [Rotate] carries no payload on purpose — why: `domain/README.md`.
 */
public sealed interface RefreshRotationDecision {
    /**
     * The presented token had not been used before: proceed with an ordinary rotation.
     */
    public data object Rotate : RefreshRotationDecision

    /**
     * The presented token had already been used — the sign a refresh token may have been stolen.
     * The whole session named by [sessionId] is revoked, not just this one token.
     */
    public data class ReuseDetected(public val sessionId: SessionId) : RefreshRotationDecision
}
