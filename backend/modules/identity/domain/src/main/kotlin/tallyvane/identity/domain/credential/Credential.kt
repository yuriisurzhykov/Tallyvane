package tallyvane.identity.domain.credential

/**
 * How a user proves who they are — a closed set of ways an account can authenticate, so a fourth
 * way is a new case here, not a free-form "kind" string threaded through every port that touches
 * one.
 *
 * A second-factor record arrives with its own slice; [PasswordRecord] and [GoogleRecord] are the
 * two primary methods built so far.
 */
public sealed interface Credential {
    public data class PasswordRecord(public val hash: PasswordHash) : Credential

    public data class GoogleRecord(public val subject: GoogleSubject) : Credential
}
