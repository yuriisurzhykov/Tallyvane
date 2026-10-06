package tallyvane.journal.contract

import tallyvane.identity.contract.AccountId
import kotlin.uuid.Uuid

/**
 * [SecurityJournal] that remembers what it was told, for the tests of whoever tells it. It keeps one line
 * for each call, in order, and writes nothing anywhere.
 */
class SecurityJournalRecorder : SecurityJournal {
    private val told = mutableListOf<String>()

    /**
     * What was told so far, one line for each call.
     */
    fun told(): List<String> = told.toList()

    override fun signedIn(account: AccountId, session: Uuid, device: DeviceFacts) {
        told += "signedIn $account $session $device"
    }

    override fun totpTurnedOn(account: AccountId, session: Uuid) {
        told += "totpTurnedOn $account $session"
    }

    override fun totpTurnedOff(account: AccountId, session: Uuid) {
        told += "totpTurnedOff $account $session"
    }

    override fun recoveryCodesReissued(account: AccountId, session: Uuid) {
        told += "recoveryCodesReissued $account $session"
    }

    override fun otherDevicesSignedOut(account: AccountId, session: Uuid) {
        told += "otherDevicesSignedOut $account $session"
    }

    override fun recoveryCodeSpent(account: AccountId, codesLeft: Int) {
        told += "recoveryCodeSpent $account $codesLeft"
    }

    override fun guessingStopped(account: AccountId) {
        told += "guessingStopped $account"
    }

    override fun toString(): String = "SecurityJournalRecorder(${told.size})"
}
