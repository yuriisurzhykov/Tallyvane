package tallyvane.journal.application

import tallyvane.identity.contract.AccountId
import tallyvane.journal.application.port.Entries
import tallyvane.journal.application.port.SecurityNotifier
import tallyvane.journal.contract.DeviceFacts
import tallyvane.journal.contract.SecurityJournal
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.platform.kernel.Clock
import kotlin.uuid.Uuid

/**
 * Writes down what the other modules tell it and tells the notifier (ADR-083, ADR-095).
 *
 * Runs inside the transaction of the act it records and never opens one, so an entry commits with the act it
 * belongs to. An entry about the second factor takes its device from the entry of the sign-in that began the
 * session it came from, so `sessions` is never asked; a session older than the journal has no such entry and the
 * entry has no device.
 */
public class Journal(private val entries: Entries, private val notifier: SecurityNotifier, private val clock: Clock) :
    SecurityJournal {
    override fun signedIn(account: AccountId, session: Uuid, device: DeviceFacts) {
        val label = LabelOf().from(device)
        val first = !entries.hasSignedInFrom(account.value, label)
        record(Entry.signedIn(account.value, clock.now(), session, label, first))
    }

    override fun totpTurnedOn(account: AccountId, session: Uuid) {
        record(Entry.totpTurnedOn(account.value, clock.now(), entries.deviceOf(session)))
    }

    override fun totpTurnedOff(account: AccountId, session: Uuid) {
        record(Entry.totpTurnedOff(account.value, clock.now(), entries.deviceOf(session)))
    }

    override fun recoveryCodesReissued(account: AccountId, session: Uuid) {
        record(Entry.recoveryCodesReissued(account.value, clock.now(), entries.deviceOf(session)))
    }

    override fun otherDevicesSignedOut(account: AccountId, session: Uuid) {
        record(Entry.otherDevicesSignedOut(account.value, clock.now(), entries.deviceOf(session)))
    }

    override fun recoveryCodeSpent(account: AccountId, codesLeft: Int) {
        record(Entry.recoveryCodeSpent(account.value, clock.now(), codesLeft))
    }

    override fun guessingStopped(account: AccountId) {
        record(Entry.guessingStopped(account.value, clock.now()))
    }

    private fun record(entry: Entry) {
        entries.add(entry)
        notifier.notify(entry)
    }

    override fun toString(): String = "Journal(entries=$entries)"

    /**
     * The device a sign-in told, as the journal keeps it.
     */
    private class LabelOf : DeviceFacts.Record {
        private val made = mutableListOf<DeviceLabel>()

        fun from(device: DeviceFacts): DeviceLabel {
            device.writeTo(this)
            return made.single()
        }

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            made += DeviceLabel(browser, platform, mobile, name)
        }
    }
}
