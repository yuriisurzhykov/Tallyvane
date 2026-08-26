package tallyvane.platform.health

/**
 * The service door to the detailed answer, until §11.2's real authentication arrives with
 * `identity`.
 *
 * Not a user session and not pretending to be one: a single secret the deploy supplies, compared to
 * a header. The precedent is `admin.tallyvane.com` sitting behind Cloudflare Access — a service
 * boundary of its own rather than a user's session — so the shape is not new to this architecture.
 * It is expected to be replaced when `identity` exists, and ADR-063 says so.
 *
 * ### Constant-time comparison
 *
 * `==` on strings returns as soon as two characters differ, so the time it takes leaks how much of a
 * guess was right — enough to recover a secret one character at a time given enough attempts. The
 * comparison here looks at every byte of both strings whatever it finds.
 *
 * The length is also compared, and that does leak the length. Hiding it would mean hashing both
 * sides first; a length is not the secret, and pretending otherwise would cost a hash on every
 * probe.
 *
 * @param expected the secret from configuration. Empty means the door is closed: no header can open
 * it, which is the right behaviour for a misconfigured deploy.
 */
public class ServiceToken(private val expected: String) {
    public fun admits(offered: String?): Boolean {
        if (expected.isEmpty() || offered == null || offered.length != expected.length) {
            return false
        }
        var difference = 0
        for (index in expected.indices) {
            difference = difference or (expected[index].code xor offered[index].code)
        }
        return difference == 0
    }
}
