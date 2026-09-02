package tallyvane.identity.domain.token

/**
 * The decision of what happens when a refresh token is presented, as a pure function over data
 * rather than a branch buried inside the use case that calls it (Policy object, Evans' DDD sense).
 *
 * Scoped to reuse detection only — "has this exact token already been used" — and nothing about
 * how long a token has been valid; see [TokenFamilyState]'s own KDoc for why the two are kept
 * apart. A plain interface rather than the design's original `object`: `no-stateful-objects`
 * refuses any non-companion `object` that declares a function, so a stateless singleton with
 * behaviour is written the same way every other port in this codebase is — an abstraction the
 * caller depends on, with [Default] as its one implementation, never named directly.
 */
public interface RefreshRotationPolicy {
    public fun decide(family: TokenFamilyState): RefreshRotationDecision

    /**
     * The one rule this policy has: a token already marked used means someone is presenting a
     * token that was already rotated away, and the whole session is revoked, not just this token.
     *
     * No I/O, so it nests on the interface rather than living as a top-level type — the same
     * reasoning `Clock.Wall` and `TokenFactory.Csprng` already rest on.
     */
    public class Default : RefreshRotationPolicy {
        override fun decide(family: TokenFamilyState): RefreshRotationDecision = if (family.used) {
            RefreshRotationDecision.ReuseDetected(family.sessionId)
        } else {
            RefreshRotationDecision.Rotate
        }
    }
}
