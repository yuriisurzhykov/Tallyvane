package tallyvane.identity.domain.credential

/**
 * How a user proves who they are — sealed because `identity` decides, once and for all, the
 * closed set of ways an account can authenticate; a fourth way is a new case here, not a
 * free-form "kind" string threaded through every port that touches one.
 *
 * Only [PasswordRecord] exists this pass. `GoogleRecord` and a second-factor record, named in the
 * design but not yet built, arrive with their own slices — see `identity/README.md`.
 */
public sealed interface Credential {
    public data class PasswordRecord(public val hash: PasswordHash) : Credential
}
