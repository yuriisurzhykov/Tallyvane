package tallyvane.journal.infrastructure

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.journal.application.ActivityPage
import tallyvane.journal.application.port.Entries
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.journal.domain.EntryKind
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [Entries] over the `journal` schema in Postgres, inside the caller's transaction (ADR-052).
 *
 * ### Pages
 *
 * An entry's number grows with every entry added, so "the entries before this number" is the next page, and no
 * entry is repeated or skipped as new ones arrive. A page asks for one more row than it shows: that row only
 * says whether there is a next page, and the cursor is the number of the last row shown.
 */
internal class PostgresEntries(private val kinds: StoredKinds) : Entries {
    override fun add(entry: Entry) {
        Row().also { entry.writeTo(it) }.insert()
    }

    override fun deviceOf(session: Uuid): DeviceLabel? = EntriesTable.selectAll()
        .where { EntriesTable.sessionId eq session }
        .singleOrNull()
        ?.let(::deviceOfRow)

    override fun hasSignedInFrom(account: Uuid, device: DeviceLabel): Boolean {
        val where = Sought()
        device.writeTo(where)
        return EntriesTable.selectAll()
            .where {
                (EntriesTable.accountId eq account) and
                    (EntriesTable.kind eq kinds.of(EntryKind.SignedIn)) and
                    (EntriesTable.deviceBrowser eq where.browser()) and
                    (EntriesTable.devicePlatform eq where.platform()) and
                    (EntriesTable.deviceMobile eq where.mobile())
            }
            .limit(1)
            .any()
    }

    override fun page(account: Uuid, before: Long?, limit: Int): ActivityPage {
        val rows = EntriesTable.selectAll()
            .where {
                if (before == null) {
                    EntriesTable.accountId eq account
                } else {
                    (EntriesTable.accountId eq account) and (EntriesTable.id less before)
                }
            }
            .orderBy(EntriesTable.id, SortOrder.DESC)
            .limit(limit + 1)
            .toList()
        val shown = rows.take(limit)
        val next = if (rows.size > limit) shown.last()[EntriesTable.id] else null
        return ActivityPage(shown.map(::entryOfRow), next)
    }

    private fun entryOfRow(row: ResultRow): Entry = Entry.restore(
        row[EntriesTable.accountId],
        kinds.from(row[EntriesTable.kind]),
        row[EntriesTable.occurredAt],
        deviceOfRow(row),
        row[EntriesTable.sessionId],
        row[EntriesTable.firstFromDevice],
        row[EntriesTable.codesLeft],
    )

    private fun deviceOfRow(row: ResultRow): DeviceLabel? {
        val browser = row[EntriesTable.deviceBrowser]
        val platform = row[EntriesTable.devicePlatform]
        val mobile = row[EntriesTable.deviceMobile]
        return if (browser == null || platform == null || mobile == null) {
            null
        } else {
            DeviceLabel(browser, platform, mobile, row[EntriesTable.deviceName])
        }
    }

    override fun toString(): String = "PostgresEntries(schema=journal)"

    /**
     * The row an entry tells: what happened first, and the device after it when there is one.
     */
    private inner class Row : Entry.Record {
        private lateinit var told: Told
        private var device: Sought? = null

        fun insert() {
            EntriesTable.insert {
                it[accountId] = told.account
                it[kind] = kinds.of(told.kind)
                it[occurredAt] = told.occurredAt
                it[sessionId] = told.session
                it[firstFromDevice] = told.firstFromDevice
                it[codesLeft] = told.codesLeft
                it[deviceBrowser] = device?.browser()
                it[devicePlatform] = device?.platform()
                it[deviceMobile] = device?.mobile()
                it[deviceName] = device?.name()
            }
        }

        override fun entry(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        ) {
            told = Told(account, kind, occurredAt, session, firstFromDevice, codesLeft)
        }

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            device = Sought().also { it.device(browser, platform, mobile, name) }
        }
    }

    private class Told(
        val account: Uuid,
        val kind: EntryKind,
        val occurredAt: Instant,
        val session: Uuid?,
        val firstFromDevice: Boolean,
        val codesLeft: Int?,
    )

    /**
     * The parts of a device kind, to look sign-ins up by.
     */
    private class Sought : DeviceLabel.Record {
        private var browser: String? = null
        private var platform: String? = null
        private var mobile: Boolean? = null
        private var name: String? = null

        fun browser(): String = checkNotNull(browser) { "A device label tells its browser." }

        fun platform(): String = checkNotNull(platform) { "A device label tells its system." }

        fun mobile(): Boolean = checkNotNull(mobile) { "A device label tells whether it is a phone." }

        fun name(): String? = name

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            this.browser = browser
            this.platform = platform
            this.mobile = mobile
            this.name = name
        }
    }
}
