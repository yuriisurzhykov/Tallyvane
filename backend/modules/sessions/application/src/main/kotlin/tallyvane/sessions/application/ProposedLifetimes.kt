package tallyvane.sessions.application

/**
 * Lifetimes as the client wrote them, in the words of the API and before anyone has checked that they mean anything
 * (ADR-098). [AddLifetimeVersionUseCase] turns them into a draft, and the draft is checked against the bounds.
 *
 * @param client The word of the kind of client, such as `browser`.
 * @param idleMillis How long a session may go unused, in milliseconds.
 * @param absoluteMillis How long a session may live, in milliseconds.
 * @param freshnessMillis How long a proof of who the person is stays fresh, in milliseconds.
 */
public class ProposedLifetimes(
    private val client: String,
    private val idleMillis: Long,
    private val absoluteMillis: Long,
    private val freshnessMillis: Long,
) {
    /**
     * Tells [record] the words.
     */
    public fun writeTo(record: Record) {
        record.lifetimes(client, idleMillis, absoluteMillis, freshnessMillis)
    }

    override fun toString(): String = "ProposedLifetimes(client=$client)"

    /**
     * What a proposal tells whoever reads it.
     */
    public fun interface Record {
        public fun lifetimes(client: String, idleMillis: Long, absoluteMillis: Long, freshnessMillis: Long)
    }
}
