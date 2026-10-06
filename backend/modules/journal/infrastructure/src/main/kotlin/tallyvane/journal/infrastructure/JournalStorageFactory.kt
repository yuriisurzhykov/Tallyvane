package tallyvane.journal.infrastructure

import org.slf4j.LoggerFactory
import tallyvane.journal.application.port.Entries
import tallyvane.journal.application.port.SecurityNotifier

/**
 * Hands out what `journal` keeps entries in and tells them with, as the ports its application layer speaks to
 * (§4.3).
 *
 * The adapter runs inside a transaction the caller opened, so it holds no connection and building it needs no
 * database.
 */
public class JournalStorageFactory {
    /**
     * Where entries are kept.
     */
    public fun entries(): Entries = PostgresEntries(StoredKinds())

    /**
     * What tells the notable ones: the log, for now.
     */
    public fun notifier(): SecurityNotifier =
        LogSecurityNotifier(LoggerFactory.getLogger(LogSecurityNotifier::class.java))

    override fun toString(): String = "JournalStorageFactory(schema=journal)"
}
