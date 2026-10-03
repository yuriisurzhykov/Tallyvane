package tallyvane.authentication.domain

import tallyvane.platform.kernel.Digest
import kotlin.time.Instant

/**
 * The recovery codes one account was given when TOTP was enabled (ADR-082): what is kept of each, and
 * whether it was spent.
 *
 * A code is kept only as the keyed digest of what the person was shown, so whoever reads the table
 * cannot sign anyone in. Each is valid once: spending marks it, and the set never offers it again. The
 * set is replaced as a whole, never grown, so a person has ten codes or none.
 *
 * Not a `data class`: its `copy` would let a spent code be unspent. State leaves through [writeTo] and
 * comes back through [restore] (ADR-085).
 */
public class RecoveryCodes private constructor(private val entries: List<Entry>) {
    /**
     * How many codes are still unspent.
     */
    public fun remaining(): Int = entries.count { !it.isSpent() }

    /**
     * The result of typing the code whose digest is [digest] at [now].
     */
    public fun spend(digest: Digest, now: Instant): SpendVerdict {
        val index = entries.indexOfFirst { !it.isSpent() && it.has(digest) }
        if (index < 0) {
            return SpendVerdict.Unknown()
        }
        val spent = entries.toMutableList()
        spent[index] = entries[index].spentAt(now)
        return SpendVerdict.Spent(RecoveryCodes(spent))
    }

    /**
     * Tells [record] every code in the order they were issued.
     */
    public fun writeTo(record: Record) {
        entries.forEach { it.writeTo(record) }
    }

    override fun toString(): String = "RecoveryCodes(remaining=${remaining()} of ${entries.size})"

    /**
     * One code: its digest, and when it was spent if it was.
     */
    private class Entry(private val digest: Digest, private val spentAt: Instant?) {
        fun isSpent(): Boolean = spentAt != null

        fun has(other: Digest): Boolean = digest == other

        fun spentAt(now: Instant): Entry = Entry(digest, now)

        fun writeTo(record: Record) {
            record.code(digest, spentAt)
        }
    }

    /**
     * What a set tells whoever keeps it, and what that keeper tells [restore] to bring it back.
     */
    public fun interface Record {
        /**
         * The next code in the set has this [digest] and was spent at [spentAt], or is unspent when null.
         */
        public fun code(digest: Digest, spentAt: Instant?)
    }

    public companion object {
        /**
         * A new set of unspent codes, one for each of [digests].
         *
         * @throws IllegalArgumentException for none, or the same digest twice: two codes that are one
         * would let a single spend look like two.
         */
        public fun issue(digests: List<Digest>): RecoveryCodes {
            require(digests.isNotEmpty()) { "A set of recovery codes with no code recovers nothing." }
            require(digests.toSet().size == digests.size) { "Two recovery codes with the same digest are one code." }
            return RecoveryCodes(digests.map { Entry(it, null) })
        }

        /**
         * The set a keeper [replay]s into the [Record] it is handed, in the order [writeTo] says things.
         *
         * @throws IllegalStateException for a replay that tells no code, or the same digest twice.
         */
        public fun restore(replay: (Record) -> Unit): RecoveryCodes {
            val told = mutableListOf<Entry>()
            val digests = mutableSetOf<Digest>()
            replay { digest, spentAt ->
                told += Entry(digest, spentAt)
                digests += digest
            }
            check(told.isNotEmpty() && digests.size == told.size) {
                "A stored set of recovery codes cannot be restored: it tells no code, or the same one twice. " +
                    "RecoveryCodes.writeTo never says that, so the rows were changed by something else; " +
                    "issue a new set instead of repairing it."
            }
            return RecoveryCodes(told)
        }
    }
}
