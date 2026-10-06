package tallyvane.journal.contract

import tallyvane.identity.contract.AccountId
import kotlin.uuid.Uuid

/**
 * What the modules that prove who a person is, and give them a session, tell the journal (ADR-083, ADR-095).
 *
 * One method for each thing that can happen, so a new kind of entry is a new method and has to be
 * thought about here. Each is called inside the transaction of the act it records and opens none: the entry
 * commits with the act, or goes with it.
 *
 * A [session] is the opaque reference of the session the request came from; the journal uses it to find the
 * device and gives it no other meaning.
 */
public interface SecurityJournal {
    /**
     * [account] signed in, and [session] is the one that sign-in began, on [device].
     */
    public fun signedIn(account: AccountId, session: Uuid, device: DeviceFacts)

    /**
     * [account] turned TOTP on from [session].
     */
    public fun totpTurnedOn(account: AccountId, session: Uuid)

    /**
     * [account] turned TOTP off from [session].
     */
    public fun totpTurnedOff(account: AccountId, session: Uuid)

    /**
     * [account] had a new set of recovery codes issued from [session].
     */
    public fun recoveryCodesReissued(account: AccountId, session: Uuid)

    /**
     * [account] signed out of every device but [session].
     */
    public fun otherDevicesSignedOut(account: AccountId, session: Uuid)

    /**
     * [account] spent a recovery code to sign in, and [codesLeft] are left. No session exists yet.
     */
    public fun recoveryCodeSpent(account: AccountId, codesLeft: Int)

    /**
     * A sign-in of [account] was closed after its wrong codes. No session exists.
     */
    public fun guessingStopped(account: AccountId)
}
