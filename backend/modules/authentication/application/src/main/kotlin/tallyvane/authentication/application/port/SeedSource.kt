package tallyvane.authentication.application.port

import java.security.SecureRandom

/**
 * Where the random bytes of a new TOTP seed come from, as a collaborator so that a test can know them.
 */
public interface SeedSource {
    /**
     * Twenty fresh random bytes: the 160 bits RFC 4226 asks of a seed.
     */
    public fun next(): ByteArray

    /**
     * The platform's cryptographically strong source.
     */
    public class Csprng : SeedSource {
        private val random = SecureRandom()

        override fun next(): ByteArray = ByteArray(BYTES).also(random::nextBytes)

        override fun toString(): String = "SeedSource.Csprng"

        private companion object {
            const val BYTES = 20
        }
    }
}
