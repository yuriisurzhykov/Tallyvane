package tallyvane.sessions.domain

/**
 * A way the person proved who they are when the session began; the session's `amr` (RFC 8176).
 *
 * `sessions` keeps its own list, and does not borrow `authentication`'s types, so how that module models its
 * factors can change without this one noticing.
 */
public enum class Factor {
    Google,
    Totp,
    RecoveryCode,
}
