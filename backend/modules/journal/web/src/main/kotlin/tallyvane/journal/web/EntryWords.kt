package tallyvane.journal.web

import tallyvane.journal.domain.EntryKind

/**
 * The words the API uses for each kind of entry, which a client branches on.
 *
 * Spelled out rather than taken from the enum's names, so renaming a constant cannot change the API.
 */
internal class EntryWords {
    fun of(kind: EntryKind): String = when (kind) {
        EntryKind.SignedIn -> "signed_in"
        EntryKind.TotpTurnedOn -> "totp_turned_on"
        EntryKind.TotpTurnedOff -> "totp_turned_off"
        EntryKind.RecoveryCodeSpent -> "recovery_code_spent"
        EntryKind.RecoveryCodesReissued -> "recovery_codes_reissued"
        EntryKind.OtherDevicesSignedOut -> "other_devices_signed_out"
        EntryKind.GuessingStopped -> "guessing_stopped"
    }

    override fun toString(): String = "EntryWords"
}
