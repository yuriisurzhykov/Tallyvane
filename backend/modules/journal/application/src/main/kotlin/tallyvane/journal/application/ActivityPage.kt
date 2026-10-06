package tallyvane.journal.application

import tallyvane.journal.domain.Entry

/**
 * One page of the journal of an account: its entries, the newest first, and where the next page begins.
 *
 * Tells them through [writeTo] and nothing else.
 */
public class ActivityPage(private val entries: List<Entry>, private val next: Long?) {
    /**
     * Tells [record] each entry in turn, then where the next page begins.
     */
    public fun writeTo(record: Record) {
        entries.forEach { it.writeTo(record) }
        record.next(next)
    }

    override fun toString(): String = "ActivityPage(${entries.size})"

    /**
     * Whoever shows a page, told each entry (with its device just after it) and then the cursor of the next page,
     * or null when this was the last.
     */
    public interface Record : Entry.Record {
        public fun next(cursor: Long?)
    }
}
