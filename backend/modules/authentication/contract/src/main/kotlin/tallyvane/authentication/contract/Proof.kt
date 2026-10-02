package tallyvane.authentication.contract

/**
 * A way a person proved who they are, as a neighbour of `authentication` may hear it.
 *
 * The session records these as its `amr` (RFC 8176), so a later step can tell how a person got in and
 * how long ago. It is `authentication`'s own vocabulary rendered for others, not its domain type: how
 * the factors are modelled there can change without `sessions` noticing.
 */
public enum class Proof {
    Google,
    Totp,
    RecoveryCode,
}
