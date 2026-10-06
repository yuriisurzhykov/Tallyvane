package tallyvane.journal.infrastructure

import org.slf4j.Logger
import tallyvane.journal.application.port.SecurityNotifier
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.journal.domain.EntryKind
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [SecurityNotifier] that writes the notable entries to the log: the kind, the account, the device and the time, and
 * nothing else (ADR-083).
 *
 * The first adapter. An email adapter takes its place without touching what decides what is notable, because
 * that is the entry's to say. A line may remain for an act that rolled back, which a log can bear (ADR-095).
 */
internal class LogSecurityNotifier(private val log: Logger) : SecurityNotifier {
    override fun notify(entry: Entry) {
        if (entry.isNotable()) {
            val told = Told().also { entry.writeTo(it) }
            log.info(
                "security event kind={} account={} device={} at={}",
                told.kind(),
                told.account(),
                told.device(),
                told.at(),
            )
        }
    }

    override fun toString(): String = "LogSecurityNotifier"

    /**
     * What an entry told, kept to be written as one line.
     */
    private class Told : Entry.Record {
        private var kind: EntryKind? = null
        private var account: Uuid? = null
        private var at: Instant? = null
        private var device: DeviceLabel? = null

        fun kind(): EntryKind = checkNotNull(kind) { "An entry tells what happened." }

        fun account(): Uuid = checkNotNull(account) { "An entry tells whose it is." }

        fun at(): Instant = checkNotNull(at) { "An entry tells when it happened." }

        fun device(): String = device?.toString() ?: "unknown"

        override fun entry(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        ) {
            this.kind = kind
            this.account = account
            this.at = occurredAt
        }

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            // Only the kind of device: the name is the person's own and the log does not need it.
            device = DeviceLabel(browser, platform, mobile, null)
        }
    }
}
