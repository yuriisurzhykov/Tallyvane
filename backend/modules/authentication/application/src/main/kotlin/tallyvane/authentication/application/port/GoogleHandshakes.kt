package tallyvane.authentication.application.port

import tallyvane.authentication.application.GoogleHandshake
import tallyvane.platform.kernel.Digest

/**
 * Where the [GoogleHandshake] of a sign-in waits while the person is at Google.
 *
 * A handshake belongs to the attempt kept under the same key, and goes when the attempt does. It is
 * used once: [take] hands it out and forgets it in the same call, so a reply from Google that arrives
 * twice, replayed or raced, finds nothing the second time.
 *
 * Both methods run inside the caller's transaction (ADR-052).
 */
public interface GoogleHandshakes {
    /**
     * Keeps [handshake] for the attempt kept under [attempt], which must already be kept.
     */
    public fun keep(attempt: Digest, handshake: GoogleHandshake)

    /**
     * The handshake kept for the attempt under [attempt], now forgotten, or null when there is none.
     */
    public fun take(attempt: Digest): GoogleHandshake?
}
