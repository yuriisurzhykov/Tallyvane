package tallyvane.journal.domain

/**
 * What an entry of the journal says happened (ADR-083, ADR-095).
 */
public enum class EntryKind {
    SignedIn,
    TotpTurnedOn,
    TotpTurnedOff,
    RecoveryCodeSpent,
    RecoveryCodesReissued,
    OtherDevicesSignedOut,
    GuessingStopped,
}
