package tallyvane.identity.domain.token

import tallyvane.identity.domain.session.SessionId

/**
 * What [RefreshRotationPolicy] decided about one presented refresh token.
 *
 * [Rotate] carries no payload. The design's own first sketch attached a freshly minted
 * [TokenPair] to it, which [RefreshRotationPolicy.decide] — a pure function, no ports, no I/O —
 * has no way to produce: minting one needs `TokenFactory`, an `application`-layer port `domain`
 * cannot see (`modules.yaml`). [Rotate] answers only "yes, proceed"; minting the new pair and
 * persisting it happens in the use case that calls this policy, after the decision, the same
 * "decide on pure data first, do the work second" order `modules/_template/README.md`'s own
 * example already follows.
 */
public sealed interface RefreshRotationDecision {
    /**
     * The presented token had not been used before: proceed with an ordinary rotation.
     */
    public data object Rotate : RefreshRotationDecision

    /**
     * The presented token had already been used — someone is presenting a token that was already
     * rotated away, the sign a refresh token may have been stolen. The whole session named by
     * [sessionId] is revoked, not just this one token.
     */
    public data class ReuseDetected(public val sessionId: SessionId) : RefreshRotationDecision
}
