package tallyvane.authentication.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGenerator

/**
 * The three secrets one trip to Google is held to (OpenID Connect, Authorization Code with PKCE).
 *
 * - `state` comes back in the redirect and proves the redirect answers a sign-in this browser began,
 *   not one an attacker began and pushed into it (login CSRF).
 * - `nonce` comes back inside Google's signed ID token and proves the token was minted for this trip,
 *   not replayed from another.
 * - `verifier` is sent with the code and proves whoever trades the code is whoever asked for it
 *   (PKCE, RFC 7636): a code that leaks through a log or a referrer is useless on its own.
 *
 * All three are drawn fresh for every sign-in and used once. They leave only through [writeTo], to
 * the adapter that speaks to Google and to the storage that keeps them while the person is away.
 */
public class GoogleHandshake(private val state: Secret, private val nonce: Secret, private val verifier: Secret) {
    /**
     * Whether [returned] is the `state` this handshake sent, compared in constant time.
     */
    public fun answers(returned: String): Boolean = state == Secret(returned)

    /**
     * Tells [record] the three secrets.
     */
    public fun writeTo(record: Record) {
        record.handshake(state, nonce, verifier)
    }

    override fun toString(): String = "GoogleHandshake(***)"

    /**
     * Whoever needs the secrets themselves: the Google adapter, and the storage.
     */
    public fun interface Record {
        public fun handshake(state: Secret, nonce: Secret, verifier: Secret)
    }

    public companion object {
        /**
         * A handshake of three secrets none of which has been used before.
         */
        public fun drawnFrom(secrets: SecretGenerator): GoogleHandshake =
            GoogleHandshake(secrets.next(), secrets.next(), secrets.next())
    }
}
