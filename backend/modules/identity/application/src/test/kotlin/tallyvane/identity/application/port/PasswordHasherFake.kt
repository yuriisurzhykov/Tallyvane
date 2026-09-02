package tallyvane.identity.application.port

import tallyvane.identity.domain.PasswordHash
import tallyvane.platform.kernel.Secret

/**
 * A [PasswordHasher] that "hashes" by prefixing the raw value. Never mistakable for a real hash —
 * there is nothing here for a test to accidentally rely on that a real Argon2id hash would not
 * also provide, since the only property [PasswordHasherConformance] checks is "verify agrees with
 * hash", which this satisfies as trivially and as correctly as the real one.
 */
internal class PasswordHasherFake : PasswordHasher {
    override fun hash(raw: Secret): PasswordHash = PasswordHash(Secret("fake:${raw.revealed()}"))

    override fun verify(raw: Secret, hash: PasswordHash): Boolean = hash.encoded == hash(raw).encoded
}
