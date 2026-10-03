package tallyvane.authentication.application.port

import tallyvane.platform.kernel.Secret
import java.security.SecureRandom

/**
 * Where a set of new recovery codes comes from, as a collaborator so that a test can know them.
 */
public interface RecoveryCodeMint {
    /**
     * Ten new codes in the form a person is shown and writes down: `ABCDE-FGHJK`.
     */
    public fun mint(): List<Secret>

    /**
     * Ten codes of ten characters drawn from an alphabet of 32 without the letters and digits that look
     * alike (`I`, `O`, `0`, `1`), which is 50 bits a code: too many to guess when each is checked against a
     * keyed digest and spent on use.
     */
    public class Csprng : RecoveryCodeMint {
        private val random = SecureRandom()

        override fun mint(): List<Secret> = List(CODES) { Secret(code()) }

        private fun code(): String {
            val characters = List(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
            return characters.chunked(LENGTH / 2).joinToString("-") { it.joinToString("") }
        }

        override fun toString(): String = "RecoveryCodeMint.Csprng"

        public companion object {
            /**
             * The 32 characters a code is written in.
             */
            public const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

            private const val CODES = 10
            private const val LENGTH = 10
        }
    }
}
