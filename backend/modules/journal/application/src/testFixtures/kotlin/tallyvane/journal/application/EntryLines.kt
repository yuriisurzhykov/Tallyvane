package tallyvane.journal.application

import tallyvane.journal.domain.Entry
import tallyvane.journal.domain.EntryKind
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What a page of the journal says, as one line for each entry, for a test to compare. The cursor of the next
 * page is [next].
 */
class EntryLines : ActivityPage.Record {
    private val lines = mutableListOf<String>()

    var next: Long? = null
        private set

    fun lines(): List<String> = lines.toList()

    override fun entry(
        account: Uuid,
        kind: EntryKind,
        occurredAt: Instant,
        session: Uuid?,
        firstFromDevice: Boolean,
        codesLeft: Int?,
    ) {
        lines += "$kind account=$account at=$occurredAt session=$session first=$firstFromDevice codes=$codesLeft"
    }

    override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
        lines[lines.lastIndex] = lines.last() + " device=$browser/$platform/$mobile/$name"
    }

    override fun next(cursor: Long?) {
        next = cursor
    }

    override fun toString(): String = "EntryLines(${lines.size})"

    companion object {
        /**
         * The lines of one entry alone.
         */
        fun of(entry: Entry): List<String> = EntryLines().also { entry.writeTo(it) }.lines()
    }
}
