package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.platform.kernel.Secret

/**
 * Turns a raw password into a [PasswordHash], and checks a raw password against one already
 * stored.
 *
 * No production implementation nests here: unlike [TokenFactory]/[TokenHasher], a real Argon2id
 * implementation reaches a third-party native library, not just the JDK, so ADR-047's nesting
 * exception does not apply — it lives as a top-level `internal` adapter in
 * `identity:infrastructure`, named by mechanism (`Argon2PasswordHasher`), the same shape
 * `PostgresJobs` would take for a database.
 */
public interface PasswordHasher {
    public fun hash(raw: Secret): PasswordHash

    public fun verify(raw: Secret, hash: PasswordHash): Boolean
}
