package tallyvane.journal.application.port

import tallyvane.journal.application.ActivityPage
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import kotlin.uuid.Uuid

/**
 * Where the entries of the journal are kept (ADR-083, ADR-095).
 *
 * Append-only: there is no way to change or delete an entry, and the adapter offers none. Every call runs
 * inside the transaction of its caller and opens none.
 */
public interface Entries {
    /**
     * Keeps [entry] after the ones kept before it.
     */
    public fun add(entry: Entry)

    /**
     * The device of the sign-in that began [session], or null when the journal has no such sign-in: the session
     * is older than the journal.
     */
    public fun deviceOf(session: Uuid): DeviceLabel?

    /**
     * Whether [account] has signed in before from a device of the same kind as [device] (browser, system, phone
     * or not).
     */
    public fun hasSignedInFrom(account: Uuid, device: DeviceLabel): Boolean

    /**
     * The entries of [account], the newest first, at most [limit] of them, and the cursor of the next page when
     * there is one. [before] is a cursor an earlier page gave, or null for the first page.
     */
    public fun page(account: Uuid, before: Long?, limit: Int): ActivityPage
}
