package tallyvane.sessions.domain

/**
 * Whether the person proved who they are recently enough for a dangerous act (ADR-092).
 *
 * Not a yes or no that anyone computes for themselves: the session says it, from when the person last
 * proved themselves and the freshness [Lifetimes] allow, so every reader agrees on what "recently" is.
 */
public enum class Freshness {
    /**
     * The last proof is recent enough.
     */
    Fresh,

    /**
     * The last proof is older than that, and the person must confirm before a dangerous act.
     */
    Stale,
}
