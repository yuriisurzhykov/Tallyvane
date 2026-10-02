package tallyvane.platform.kernel

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a [Secret] into the only thing about it the database may hold (ADR-079).
 *
 * A digest is enough to find what a secret opens and useless for opening it: whoever reads the
 * table, or a backup of it, holds no sign-in and no session.
 */
public interface Digests {
    /**
     * What is kept of [secret]: the same secret always gives the same digest, so the digest is what
     * a lookup asks for.
     */
    public fun of(secret: Secret): Digest

    /**
     * HMAC-SHA256 under a key that lives outside the database, the "pepper" of ADR-079.
     *
     * A plain SHA-256 would already be impossible to reverse for 256 random bits. The key adds what
     * the hash alone cannot: someone who can *write* to the database still cannot plant a row for a
     * secret of their own choosing, because they cannot compute its digest. Fast on purpose: slow
     * hashing protects secrets people choose, and nobody chooses these.
     *
     * The [version] is written next to every digest, so a rotated key can tell its own digests from
     * the old one's.
     *
     * It nests on the port because it reaches no technology, only the JVM's own `Mac`.
     */
    public class Hmac(private val pepper: Secret, private val version: Int) : Digests {
        init {
            require(version >= 1) { "A pepper version counts from 1, but was given $version." }
            require(pepper.revealed().length >= MIN_PEPPER) {
                "A pepper shorter than $MIN_PEPPER characters can be guessed, and then every digest it made " +
                    "can be computed by whoever guessed it."
            }
        }

        override fun of(secret: Secret): Digest {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(pepper.revealed().toByteArray(), ALGORITHM))
            return Digest(mac.doFinal(secret.revealed().toByteArray()), version)
        }

        override fun toString(): String = "Digests.Hmac(version=$version)"

        private companion object {
            const val ALGORITHM = "HmacSHA256"

            const val MIN_PEPPER = 32
        }
    }
}
