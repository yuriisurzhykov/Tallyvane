package tallyvane.platform.kernel

import java.security.SecureRandom
import java.util.Base64

/**
 * Unguessable values as a collaborator: the secret a sign-in attempt or a session is carried by.
 *
 * [IdGenerator] is the wrong source for these, and on purpose: a UUIDv7 publishes the moment it was
 * minted and spends at most 74 bits on randomness. A value that lets whoever holds it act as someone
 * needs nothing but randomness, and enough of it that guessing is not a plan (ADR-079).
 *
 * Every secret is handed out as a [Secret], so it is redacted in logs and compared in constant time
 * from the moment it exists. What is kept of it is a [Digest] from [Digests], never the value.
 */
public interface SecretGenerator {
    /**
     * A new secret, unrelated to every other this generator gave.
     */
    public fun next(): Secret

    /**
     * 256 bits from the platform's cryptographically strong source, written in the URL-safe Base64
     * alphabet without padding, so a secret fits a cookie, a query string and a header alike.
     *
     * It nests on the port because it reaches no technology, only the JVM's own `SecureRandom`.
     */
    public class Csprng : SecretGenerator {
        private val random = SecureRandom()

        override fun next(): Secret {
            val bytes = ByteArray(BYTES)
            random.nextBytes(bytes)
            return Secret(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }

        private companion object {
            /**
             * 256 bits: ADR-079 asks this much of a session token, and nothing that carries a sign-in
             * deserves less.
             */
            const val BYTES = 32
        }
    }
}
