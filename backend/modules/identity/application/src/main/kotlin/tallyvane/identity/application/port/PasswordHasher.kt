package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.platform.kernel.Secret

/**
 * Turns a raw password into a [PasswordHash], and checks a raw password against one already
 * stored.
 *
 * Why the real implementation is a top-level class in `identity:infrastructure`, not nested here:
 * `infrastructure/README.md`.
 */
public interface PasswordHasher {
    public fun hash(raw: Secret): PasswordHash

    public fun verify(raw: Secret, hash: PasswordHash): Boolean
}
