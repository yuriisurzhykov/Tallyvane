package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Instant

/**
 * The versions kept for one kind of client, newest first, and which of them is in force (ADR-078, ADR-098).
 *
 * What a screen of the versions needs: for each, who made it, when and why, and its own words; for the one in
 * force, since when, by whom and why. A version made by a migration, such as the first of each kind, has no author
 * and no comment, and says so.
 *
 * Its state leaves through [writeTo] only.
 *
 * @param entries Every version kept, newest first.
 * @param inForce The activation that put a version in force, or null when none was ever activated.
 */
public class KeptLifetimes(private val entries: List<Entry>, private val inForce: InForce?) {
    /**
     * The version numbered [number], or null when none is.
     */
    public fun numbered(number: Int): LifetimeVersion? = entries.firstOrNull { it.isNumbered(number) }?.version()

    /**
     * Tells [record] about each version in turn, newest first.
     */
    public fun writeTo(record: Record) {
        entries.forEach { entry -> entry.writeTo(record, inForce) }
    }

    override fun toString(): String = "KeptLifetimes(versions=${entries.size})"

    /**
     * One version as it is kept: the version, and who made it, when and why.
     *
     * @param madeBy Who made it, or null for a version a migration made.
     * @param comment Why, or null for a version a migration made.
     */
    public class Entry(
        private val version: LifetimeVersion,
        private val madeBy: AccountId?,
        private val comment: String?,
        private val madeAt: Instant,
    ) {
        internal fun version(): LifetimeVersion = version

        internal fun isNumbered(number: Int): Boolean = Numbered().of(version) == number

        internal fun writeTo(record: Record, inForce: InForce?) {
            record.kept(madeBy, comment, madeAt)
            if (inForce != null && inForce.puts(Numbered().of(version))) {
                inForce.writeTo(record)
            }
            version.writeTo(record)
        }

        override fun toString(): String = "Entry(version=$version)"
    }

    /**
     * The last activation of a kind of client, which is what puts a version in force.
     *
     * @param number The version it put in force.
     * @param by Who activated it, or null for an activation a migration made.
     * @param reason Why, or null for an activation a migration made.
     * @param at When.
     */
    public class InForce(
        private val number: Int,
        private val by: AccountId?,
        private val reason: String?,
        private val at: Instant,
    ) {
        internal fun puts(version: Int): Boolean = number == version

        internal fun writeTo(record: Record) {
            record.inForce(at, by, reason)
        }

        override fun toString(): String = "InForce(number=$number)"
    }

    /**
     * What the versions tell whoever shows them. For each version, newest first: who made it and why ([kept]); if
     * it is the one in force, since when ([inForce]); then the version's own words, the number first.
     */
    public interface Record : LifetimeVersion.Record {
        /**
         * The next version was made by [madeBy] at [madeAt] for the reason [comment]; both are null for a version
         * a migration made.
         */
        public fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant)

        /**
         * The version just told is the one in force, from [since]: activated by [by] for the reason [reason], both
         * null for an activation a migration made.
         */
        public fun inForce(since: Instant, by: AccountId?, reason: String?)
    }
}
