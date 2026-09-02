package tallyvane.identity.domain

/**
 * The decision of what happens when a refresh token is presented, as a pure function over data
 * rather than a branch buried inside the use case that calls it (Policy object, Evans' DDD sense).
 *
 * Scoped to reuse detection only — "has this exact token already been used" — and nothing about
 * how long a token has been valid; see [TokenFamilyState]'s own KDoc for why the two are kept
 * apart.
 *
 * An interface, not a bare function or a stateless `object`, for the same reason every port in
 * this codebase is: the caller — the future `RefreshSessionUseCase` — depends on this abstraction,
 * never on [Default] by name, so its own tests can substitute a fake the day they need one instead
 * of running this policy's real logic indirectly.
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
