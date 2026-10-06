package tallyvane.journal.infrastructure

import tallyvane.journal.domain.EntryKind

/**
 * The words the migration's `check` constraint accepts for each [EntryKind], and back.
 *
 * Spelled out rather than taken from the enum's names, so renaming a constant cannot change what is stored.
 */
internal class StoredKinds {
    fun of(kind: EntryKind): String = when (kind) {
        EntryKind.SignedIn -> "signed_in"
        EntryKind.TotpTurnedOn -> "totp_turned_on"
        EntryKind.TotpTurnedOff -> "totp_turned_off"
        EntryKind.RecoveryCodeSpent -> "recovery_code_spent"
        EntryKind.RecoveryCodesReissued -> "recovery_codes_reissued"
        EntryKind.OtherDevicesSignedOut -> "other_devices_signed_out"
        EntryKind.GuessingStopped -> "guessing_stopped"
    }

    fun from(word: String): EntryKind = EntryKind.entries.firstOrNull { of(it) == word }
        ?: error("An entry was kept as '$word', which the journal schema does not accept.")

    override fun toString(): String = "StoredKinds"
}
