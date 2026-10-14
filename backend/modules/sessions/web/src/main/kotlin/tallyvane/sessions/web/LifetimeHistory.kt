package tallyvane.sessions.web

import kotlinx.serialization.Serializable
import tallyvane.identity.contract.AccountId
import tallyvane.sessions.application.KeptLifetimes
import tallyvane.sessions.domain.ClientType
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The versions of the lifetimes of one kind of client, newest first (ADR-098).
 *
 * @param client The word of the kind of client, such as `browser`.
 * @param versions Every version kept. The one in force says so in [LifetimeVersionBody.inForce].
 */
@Serializable
internal class LifetimeHistory(val client: String, val versions: List<LifetimeVersionBody>) {
    /**
     * One version: its number, who made it, when and why, and its numbers.
     *
     * @param madeBy The account that made it, or none for a version a migration made.
     * @param comment Why, or none for a version a migration made.
     * @param inForce Since when the version has been the one in force, by whom and why; absent for every version but
     * one.
     * @param idleMillis How long a session may go unused.
     * @param absoluteMillis How long a session may live, however much it is used.
     * @param freshnessMillis How long a proof of who the person is stays fresh for a dangerous act.
     */
    @Serializable
    class LifetimeVersionBody(
        val number: Int,
        val madeBy: String?,
        val madeAt: String,
        val comment: String?,
        val inForce: InForceBody?,
        val idleMillis: Long,
        val absoluteMillis: Long,
        val freshnessMillis: Long,
    )

    /**
     * That a version is the one in force: since when, by whom and why. [by] and [reason] are absent for an
     * activation a migration made.
     */
    @Serializable
    class InForceBody(val since: String, val by: String?, val reason: String?)

    /**
     * Reads what [KeptLifetimes] tells into the body, one version at a time.
     */
    class Reading : KeptLifetimes.Record {
        private val versions = mutableListOf<LifetimeVersionBody>()
        private var madeBy: AccountId? = null
        private var comment: String? = null
        private var madeAt: Instant? = null
        private var inForce: InForceBody? = null
        private var number = 0

        /**
         * The body of what was told.
         *
         * @param client The word of the kind of client the versions are for, which an empty history does not tell.
         */
        fun of(client: String): LifetimeHistory = LifetimeHistory(client, versions.toList())

        override fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant) {
            this.madeBy = madeBy
            this.comment = comment
            this.madeAt = madeAt
            this.inForce = null
        }

        override fun inForce(since: Instant, by: AccountId?, reason: String?) {
            inForce = InForceBody(since.toString(), by?.value?.toString(), reason)
        }

        override fun number(number: Int) {
            this.number = number
        }

        override fun client(client: ClientType) = Unit

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
            versions += LifetimeVersionBody(
                number,
                madeBy?.value?.toString(),
                checkNotNull(madeAt).toString(),
                comment,
                inForce,
                idle.inWholeMilliseconds,
                absolute.inWholeMilliseconds,
                freshness.inWholeMilliseconds,
            )
        }

        override fun toString(): String = "Reading(versions=${versions.size})"
    }
}
