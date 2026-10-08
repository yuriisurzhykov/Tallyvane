package tallyvane.authentication.application.port

import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleHandshake
import tallyvane.platform.kernel.Surface

/**
 * Google, as the one method of signing in that identifies a person (ADR-077): OpenID Connect,
 * Authorization Code with PKCE, run by the backend.
 *
 * The [GoogleHandshake] is what ties the two halves together: [addressFor] sends its `state`, `nonce`
 * and PKCE challenge to Google, and [exchange] proves the code was meant for this handshake by sending
 * the verifier and checking the nonce in the token that comes back.
 *
 * Google sends the browser back to an address registered with it, and the two doors (ADR-097) have one each, so
 * both halves are told which [Surface] the person is on: the address sent and the address the code is traded
 * against must be the same one.
 */
public interface Google {
    /**
     * Where to send the browser so the person on [surface] can sign in at Google for [handshake].
     */
    public fun addressFor(handshake: GoogleHandshake, surface: Surface): String

    /**
     * Trades the [code] Google sent back to [surface] for the identity it vouches for.
     *
     * Every way that can fail is an answer rather than an exception, including Google being
     * unreachable: the person is in the middle of a redirect and must land on a page that says what
     * happened, not on an error document.
     */
    public suspend fun exchange(code: String, handshake: GoogleHandshake, surface: Surface): GoogleAnswer
}
