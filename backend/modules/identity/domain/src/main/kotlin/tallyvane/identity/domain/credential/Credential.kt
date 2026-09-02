package tallyvane.identity.domain.credential

/**
 * How a user proves who they are — a closed set of ways an account can authenticate, so a fourth
 * way is a new case here, not a free-form "kind" string threaded through every port that touches
 * one.
 *
 * Only [PasswordRecord] exists this pass; `GoogleRecord` and a second-factor record arrive with
 * their own slices.
 */
public sealed interface Credential {
    public data class PasswordRecord(public val hash: PasswordHash) : Credential
}
