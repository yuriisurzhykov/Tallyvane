package tallyvane.identity.domain.token

/**
 * Decides what to do with a presented refresh token: rotate if unseen, flag the session for
 * revocation if it has already been used.
 *
 * ```
 * decide(TokenFamilyState(sessionId, used = false)) // -> Rotate
 * decide(TokenFamilyState(sessionId, used = true))   // -> ReuseDetected(sessionId)
 * ```
 *
 * Scoped to reuse detection only, nothing about how long a token has been valid — why:
 * `domain/README.md`.
 */
public interface RefreshRotationPolicy {
    public fun decide(family: TokenFamilyState): RefreshRotationDecision

    /**
     * No I/O, so it nests here rather than living as a top-level type.
     */
    public class Default : RefreshRotationPolicy {
        override fun decide(family: TokenFamilyState): RefreshRotationDecision = if (family.used) {
            RefreshRotationDecision.ReuseDetected(family.sessionId)
        } else {
            RefreshRotationDecision.Rotate
        }
    }
}
