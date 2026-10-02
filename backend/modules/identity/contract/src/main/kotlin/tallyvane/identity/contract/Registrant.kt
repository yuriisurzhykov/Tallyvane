package tallyvane.identity.contract

import kotlin.time.Instant

/**
 * A person about to have an account: what Google vouched for and what they confirmed on the welcome
 * screen (ADR-077, slice 3).
 *
 * @param googleSubject Google's `sub` for them, the key the account is found by from now on.
 * @param displayName The name they chose, as typed; `identity` decides whether it is acceptable.
 * @param email The address Google reported as verified. Kept to reach the person, never to find them.
 * @param consentedAt When they agreed to the privacy policy. There is no registrant who did not: the
 * welcome screen does not let anyone continue without it.
 */
public class Registrant(
    private val googleSubject: String,
    private val displayName: String,
    private val email: String,
    private val consentedAt: Instant,
) {
    /**
     * Tells [record] everything about this registrant, for the one place that turns it into an account.
     */
    public fun writeTo(record: Record) {
        record.registrant(googleSubject, displayName, email, consentedAt)
    }

    // The subject and the address identify a real person; a log line about a registration needs neither.
    override fun toString(): String = "Registrant(consentedAt=$consentedAt)"

    /**
     * Whoever turns a registrant into an account.
     */
    public fun interface Record {
        public fun registrant(googleSubject: String, displayName: String, email: String, consentedAt: Instant)
    }
}
