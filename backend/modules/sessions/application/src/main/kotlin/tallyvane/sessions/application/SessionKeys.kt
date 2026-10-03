package tallyvane.sessions.application

import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.sessions.domain.SessionId

/**
 * The secrets of sessions and the keys they are kept under (ADR-079).
 *
 * The browser holds a random secret in its `__Host-session` cookie; the database holds only its keyed
 * digest, so the same secret always finds the same session and nothing in the table can be presented.
 */
public class SessionKeys(
    private val secrets: SecretGenerator,
    private val digests: Digests,
    private val ids: IdGenerator,
) {
    /**
     * A new secret for a new session, with the key that session is kept under and the id it is known by.
     * The id is not derived from the secret: knowing one gives nothing of the other.
     */
    public fun issue(): Issued {
        val secret = secrets.next()
        return Issued(secret, digests.of(secret), SessionId(ids.next()))
    }

    /**
     * The key of the session whose secret a browser presented.
     */
    public fun keyOf(secret: Secret): Digest = digests.of(secret)

    override fun toString(): String = "SessionKeys(digests=$digests)"

    /**
     * A secret, the key it is kept under and the id of the session. The secret goes to the browser, the
     * key and the id to storage.
     */
    public class Issued(public val secret: Secret, public val key: Digest, public val id: SessionId) {
        override fun toString(): String = "Issued(***)"
    }
}
