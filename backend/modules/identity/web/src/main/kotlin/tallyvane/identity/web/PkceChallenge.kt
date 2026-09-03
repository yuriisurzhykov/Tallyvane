package tallyvane.identity.web

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One RFC 7636 verifier/challenge pair plus an independent CSRF `state` value, generated fresh for
 * every `GET /api/v1/auth/google/oauth/start` call. [TokenFactory.Csprng][tallyvane.identity.application.port.TokenFactory.Csprng]
 * is not reused here on purpose: that type mints `identity`'s own session tokens, and this is an
 * unrelated, shorter-lived value with a different shape (RFC 7636's own 43-character minimum),
 * that this module's own session store never sees or hashes.
 */
internal class PkceChallenge {
    private val random = SecureRandom()

    fun generate(): Generated {
        val verifier = randomUrlSafe(VERIFIER_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return Generated(state = randomUrlSafe(STATE_BYTES), verifier = verifier, challenge = challenge)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    /**
     * @property state Sent to Google and read back unchanged at the callback — proves the
     * callback belongs to the redirect this process itself started.
     * @property verifier Kept only in the short-lived cookie the callback reads back; never sent
     * to Google until the token exchange itself.
     * @property challenge `SHA-256(verifier)`, base64url-encoded — sent to Google at the start so
     * the token endpoint can check the callback's own `verifier` against it later.
     */
    data class Generated(val state: String, val verifier: String, val challenge: String)

    private companion object {
        const val VERIFIER_BYTES = 32
        const val STATE_BYTES = 16
    }
}
