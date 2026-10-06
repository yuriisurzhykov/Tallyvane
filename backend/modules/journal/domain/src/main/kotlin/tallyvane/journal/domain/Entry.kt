package tallyvane.journal.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One line of the security journal: something that happened to an account, when, and on which device (ADR-083).
 *
 * Written once and never changed. A sign-in carries the reference of the session it began, so that the entries
 * about the second factor that follow from that session can name its device without anyone asking `sessions`
 * (ADR-095). No token, code or secret is ever a field of an entry.
 *
 * Not a `data class`: a generated `copy()` would be a public way to change a line of a log.
 */
public class Entry private constructor(
    private val account: Uuid,
    private val kind: EntryKind,
    private val occurredAt: Instant,
    private val device: DeviceLabel?,
    private val session: Uuid?,
    private val firstFromDevice: Boolean,
    private val codesLeft: Int?,
) {
    init {
        require(codesLeft == null || codesLeft >= 0) { "A count of codes left is not negative." }
    }

    /**
     * Whether the person would want to be told about it as it happens, not only to find it when they look. What is
     * notable is the journal's to say, so an adapter that sends it somewhere only carries out the answer.
     */
    public fun isNotable(): Boolean = when (kind) {
        EntryKind.SignedIn -> firstFromDevice
        EntryKind.TotpTurnedOff,
        EntryKind.RecoveryCodeSpent,
        EntryKind.OtherDevicesSignedOut,
        EntryKind.GuessingStopped,
        -> true
        EntryKind.TotpTurnedOn,
        EntryKind.RecoveryCodesReissued,
        -> false
    }

    /**
     * Tells [record] the entry, and then the device if one is known.
     */
    public fun writeTo(record: Record) {
        record.entry(account, kind, occurredAt, session, firstFromDevice, codesLeft)
        device?.writeTo { browser, platform, mobile, name -> record.device(browser, platform, mobile, name) }
    }

    override fun equals(other: Any?): Boolean = other is Entry &&
        other.account == account &&
        other.kind == kind &&
        other.occurredAt == occurredAt &&
        other.device == device &&
        other.session == session &&
        other.firstFromDevice == firstFromDevice &&
        other.codesLeft == codesLeft

    override fun hashCode(): Int =
        listOf(account, kind, occurredAt, device, session, firstFromDevice, codesLeft).hashCode()

    override fun toString(): String = "Entry($kind)"

    /**
     * Whoever keeps or shows an entry, told what it says.
     */
    public interface Record {
        public fun entry(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        )

        /**
         * Told after [entry], and only when the device is known.
         */
        public fun device(browser: String, platform: String, mobile: Boolean, name: String?)
    }

    public companion object {
        /**
         * [account] signed in on [device], and [session] is what that sign-in began.
         */
        public fun signedIn(
            account: Uuid,
            occurredAt: Instant,
            session: Uuid,
            device: DeviceLabel,
            firstFromDevice: Boolean,
        ): Entry = Entry(account, EntryKind.SignedIn, occurredAt, device, session, firstFromDevice, null)

        /**
         * [account] turned TOTP on, on [device], which may be unknown.
         */
        public fun totpTurnedOn(account: Uuid, occurredAt: Instant, device: DeviceLabel?): Entry =
            Entry(account, EntryKind.TotpTurnedOn, occurredAt, device, null, false, null)

        /**
         * [account] turned TOTP off, on [device], which may be unknown.
         */
        public fun totpTurnedOff(account: Uuid, occurredAt: Instant, device: DeviceLabel?): Entry =
            Entry(account, EntryKind.TotpTurnedOff, occurredAt, device, null, false, null)

        /**
         * [account] had new recovery codes issued, on [device], which may be unknown.
         */
        public fun recoveryCodesReissued(account: Uuid, occurredAt: Instant, device: DeviceLabel?): Entry =
            Entry(account, EntryKind.RecoveryCodesReissued, occurredAt, device, null, false, null)

        /**
         * [account] signed out of every device but the one asking, on [device], which may be unknown.
         */
        public fun otherDevicesSignedOut(account: Uuid, occurredAt: Instant, device: DeviceLabel?): Entry =
            Entry(account, EntryKind.OtherDevicesSignedOut, occurredAt, device, null, false, null)

        /**
         * [account] spent a recovery code, and [codesLeft] remain. The sign-in it belongs to has no session yet, so
         * no device.
         */
        public fun recoveryCodeSpent(account: Uuid, occurredAt: Instant, codesLeft: Int): Entry =
            Entry(account, EntryKind.RecoveryCodeSpent, occurredAt, null, null, false, codesLeft)

        /**
         * A sign-in of [account] was closed after its wrong codes.
         */
        public fun guessingStopped(account: Uuid, occurredAt: Instant): Entry =
            Entry(account, EntryKind.GuessingStopped, occurredAt, null, null, false, null)

        /**
         * The entry storage kept, from the parts it kept.
         *
         * @throws IllegalArgumentException the count of codes left is negative.
         */
        public fun restore(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            device: DeviceLabel?,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        ): Entry = Entry(account, kind, occurredAt, device, session, firstFromDevice, codesLeft)
    }
}
