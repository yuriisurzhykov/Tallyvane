package tallyvane.journal.application

import tallyvane.journal.application.port.Entries
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.journal.domain.EntryKind
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [Entries] in a list, for tests of the code that uses the port (ADR-044). [EntriesFakeSpec] holds it to the
 * suite the adapter over Postgres passes.
 */
class EntriesFake : Entries {
    private val kept = mutableListOf<Row>()

    override fun add(entry: Entry) {
        kept += Row(kept.size + 1L, entry, Seen().also { entry.writeTo(it) })
    }

    override fun deviceOf(session: Uuid): DeviceLabel? =
        kept.firstOrNull { it.seen.session == session && it.seen.kind == EntryKind.SignedIn }?.seen?.device

    override fun hasSignedInFrom(account: Uuid, device: DeviceLabel): Boolean = kept.any { row ->
        row.seen.account == account &&
            row.seen.kind == EntryKind.SignedIn &&
            row.seen.device?.sameKindAs(device) == true
    }

    override fun page(account: Uuid, before: Long?, limit: Int): ActivityPage {
        val older = kept.filter { it.seen.account == account && (before == null || it.number < before) }
            .sortedByDescending { it.number }
        val shown = older.take(limit)
        val next = if (older.size > limit) shown.last().number else null
        return ActivityPage(shown.map { it.entry }, next)
    }

    override fun toString(): String = "EntriesFake(kept=${kept.size})"

    private class Row(val number: Long, val entry: Entry, val seen: Seen)

    /**
     * What an entry told, kept to answer the questions the port asks.
     */
    private class Seen : Entry.Record {
        var account: Uuid? = null
        var kind: EntryKind? = null
        var session: Uuid? = null
        var device: DeviceLabel? = null

        override fun entry(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        ) {
            this.account = account
            this.kind = kind
            this.session = session
        }

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            device = DeviceLabel(browser, platform, mobile, name)
        }
    }
}
