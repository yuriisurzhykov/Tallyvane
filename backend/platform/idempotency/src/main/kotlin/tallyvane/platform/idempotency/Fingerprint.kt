package tallyvane.platform.idempotency

import java.security.MessageDigest

/**
 * What a request amounted to, boiled down to 32 bytes, so that a repeat can be told from a different
 * request wearing the same key.
 *
 * The same key with a different fingerprint is a client that reused a key, and running it would answer
 * one request with another's result. It is refused before anything runs (ADR-086).
 *
 * SHA-256 over the method, the request target (path and query, as sent) and the body. A separator that
 * cannot occur in the first two keeps `("a", "bc")` from colliding with `("ab", "c")`: a method has no
 * NUL and a request target may not carry one.
 */
public class Fingerprint private constructor(private val digest: ByteArray) {
    /**
     * Said to the claim that holds it, and to nobody else: storage reads it through
     * [Claim.writeTo]. A copy, so nobody can change what the claim holds.
     */
    internal fun bytes(): ByteArray = digest.copyOf()

    override fun toString(): String = "Fingerprint(sha256=${digest.joinToString("") { "%02x".format(it) }})"

    public companion object {
        private const val SEPARATOR = 0

        /**
         * @param method the HTTP method, as received.
         * @param target the request target: path and query.
         * @param body every byte of the body, which may be none.
         */
        public fun of(method: String, target: String, body: ByteArray): Fingerprint {
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update(method.toByteArray())
            hash.update(SEPARATOR.toByte())
            hash.update(target.toByteArray())
            hash.update(SEPARATOR.toByte())
            hash.update(body)
            return Fingerprint(hash.digest())
        }
    }
}
