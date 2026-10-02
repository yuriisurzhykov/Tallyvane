package tallyvane.identity.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A person known to the system: who Google says they are, what they are called, how to reach them,
 * and when they agreed to the privacy policy (ADR-077, §8.3).
 *
 * Created once, at registration, and told to storage through [writeTo]. Nothing reads one back yet:
 * the only question asked of accounts so far is "whose is this Google subject", which storage answers
 * with the id alone.
 *
 * @param id The account's identifier, from the id generator.
 * @param googleSubject Google's `sub`, the key the person is found by.
 * @param name What they chose to be called.
 * @param email The address Google reported as verified.
 * @param registeredAt When the account was created, which is also when they consented: the welcome
 * screen does both in one step.
 */
public class Account(
    private val id: Uuid,
    private val googleSubject: String,
    private val name: DisplayName,
    private val email: String,
    private val registeredAt: Instant,
) {
    init {
        require(googleSubject.isNotBlank()) { "An account Google names nobody for cannot be found again." }
        require(email.isNotBlank()) { "Google reports a verified address for everyone it vouches for." }
    }

    /**
     * Tells [record] everything about this account.
     */
    public fun writeTo(record: Record) {
        name.writeTo { displayName -> record.account(id, googleSubject, displayName, email, registeredAt) }
    }

    override fun toString(): String = "Account(id=$id, registeredAt=$registeredAt)"

    /**
     * Whoever keeps an account.
     */
    public fun interface Record {
        public fun account(id: Uuid, googleSubject: String, displayName: String, email: String, registeredAt: Instant)
    }
}
