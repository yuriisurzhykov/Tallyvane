package tallyvane.authentication.application

import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGenerator

/**
 * The secrets of a sign-in and the keys they are kept under (slice 3, fork 2).
 *
 * The browser holds a random secret in its `__Host-attempt` cookie; the database holds only its keyed
 * digest. So whoever reads the table cannot continue anyone's sign-in, and the same secret always
 * finds the same attempt.
 */
public class SignInKeys(private val secrets: SecretGenerator, private val digests: Digests) {
    /**
     * A new secret for a new attempt, with the key that attempt is kept under.
     */
    public fun issue(): IssuedKey {
        val secret = secrets.next()
        return IssuedKey(secret, digests.of(secret))
    }

    /**
     * The key of the attempt whose secret a browser presented.
     */
    public fun keyOf(secret: Secret): Digest = digests.of(secret)

    /**
     * The three secrets of one trip to Google.
     */
    public fun handshake(): GoogleHandshake = GoogleHandshake.drawnFrom(secrets)

    override fun toString(): String = "SignInKeys(digests=$digests)"
}
