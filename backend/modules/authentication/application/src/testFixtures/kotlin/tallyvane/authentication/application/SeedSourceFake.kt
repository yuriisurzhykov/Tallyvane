package tallyvane.authentication.application

import tallyvane.authentication.application.port.SeedSource

/**
 * A [SeedSource] whose first seed is `12345678901234567890`, the secret of RFC 4226's own vectors, so a
 * test can know the key without asking the code under test. Each later seed differs from the one before
 * in its first byte, so a test can tell that a new seed was made.
 */
class SeedSourceFake : SeedSource {
    private var made = 0

    override fun next(): ByteArray {
        val seed = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        seed[0] = (seed[0] + made).toByte()
        made += 1
        return seed
    }

    override fun toString(): String = "SeedSourceFake($made)"
}
