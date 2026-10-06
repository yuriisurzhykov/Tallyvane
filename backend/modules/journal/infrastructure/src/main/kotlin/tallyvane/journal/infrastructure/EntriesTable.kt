package tallyvane.journal.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `journal.entries`: one row for each entry, only ever added.
 */
internal object EntriesTable : Table("journal.entries") {
    val id = long("id").autoIncrement()
    val accountId = uuid("account_id")
    val kind = text("kind")
    val occurredAt = timestamp("occurred_at")
    val deviceBrowser = text("device_browser").nullable()
    val devicePlatform = text("device_platform").nullable()
    val deviceMobile = bool("device_mobile").nullable()
    val deviceName = text("device_name").nullable()
    val sessionId = uuid("session_id").nullable()
    val firstFromDevice = bool("first_from_device")
    val codesLeft = integer("codes_left").nullable()

    override val primaryKey = PrimaryKey(id)
}
