package tallyvane.sessions.web

import kotlinx.serialization.Serializable
import tallyvane.sessions.application.ProposedLifetimes

/**
 * New lifetimes as the administrator wrote them (ADR-098).
 *
 * @param client The word of the kind of client, such as `browser`.
 * @param idleMillis How long a session may go unused, in milliseconds.
 * @param absoluteMillis How long a session may live, in milliseconds.
 * @param freshnessMillis How long a proof of who the person is stays fresh, in milliseconds.
 * @param comment Why, in the administrator's words.
 */
@Serializable
internal class LifetimeProposal(
    val client: String,
    val idleMillis: Long,
    val absoluteMillis: Long,
    val freshnessMillis: Long,
    val comment: String,
) {
    /**
     * The lifetimes these words describe, for the use case to check.
     */
    fun lifetimes(): ProposedLifetimes = ProposedLifetimes(client, idleMillis, absoluteMillis, freshnessMillis)
}
