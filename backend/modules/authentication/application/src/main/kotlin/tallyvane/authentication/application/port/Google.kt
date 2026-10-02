package tallyvane.authentication.application.port

import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleHandshake

/**
 * Google, as the one method of signing in that identifies a person (ADR-077): OpenID Connect,
 * Authorization Code with PKCE, run by the backend.
 *
 * The [GoogleHandshake] is what ties the two halves together: [addressFor] sends its `state`, `nonce`
 * and PKCE challenge to Google, and [exchange] proves the code was meant for this handshake by sending
 * the verifier and checking the nonce in the token that comes back.
 */
public interface Google {
    /**
     * Where to send the browser so the person can sign in at Google for [handshake].
     */
    public fun addressFor(handshake: GoogleHandshake): String

    /**
     * Trades the [code] Google sent back for the identity it vouches for.
     *
     * Every way that can fail is an answer rather than an exception, including Google being
     * unreachable: the person is in the middle of a redirect and must land on a page that says what
     * happened, not on an error document.
     */
    public suspend fun exchange(code: String, handshake: GoogleHandshake): GoogleAnswer
}
