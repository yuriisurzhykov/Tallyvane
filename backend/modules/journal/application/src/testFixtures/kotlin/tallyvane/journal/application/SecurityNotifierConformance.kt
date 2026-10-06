package tallyvane.journal.application

import io.kotest.core.spec.style.StringSpec
import tallyvane.journal.application.port.SecurityNotifier
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ME = Uuid.parse("0199a000-0000-7000-8000-000000000001")
private val SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a1")
private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val DEVICE = DeviceLabel("chrome", "windows", false, "Work")

/**
 * The behaviour every [SecurityNotifier] must show, inherited by the fake and by the adapters, so none of them
 * stops an act: the notifier runs inside the transaction of the act, and what it throws takes the act with it.
 */
abstract class SecurityNotifierConformance : StringSpec() {
    /**
     * A notifier with nothing told to it yet.
     */
    protected abstract fun fresh(): SecurityNotifier

    init {
        "takes an entry of every kind, notable or not, without refusing it" {
            val notifier = fresh()

            allKinds().forEach(notifier::notify)
        }

        "takes the same entry twice" {
            val notifier = fresh()
            val entry = Entry.totpTurnedOff(ME, AT, DEVICE)

            notifier.notify(entry)
            notifier.notify(entry)
        }

        "takes an entry that names no device" {
            fresh().notify(Entry.totpTurnedOff(ME, AT, null))
        }
    }

    private fun allKinds(): List<Entry> = listOf(
        Entry.signedIn(ME, AT, SESSION, DEVICE, true),
        Entry.signedIn(ME, AT, SESSION, DEVICE, false),
        Entry.totpTurnedOn(ME, AT, DEVICE),
        Entry.totpTurnedOff(ME, AT, DEVICE),
        Entry.recoveryCodeSpent(ME, AT, 4),
        Entry.recoveryCodesReissued(ME, AT, DEVICE),
        Entry.otherDevicesSignedOut(ME, AT, DEVICE),
        Entry.guessingStopped(ME, AT),
    )
}
