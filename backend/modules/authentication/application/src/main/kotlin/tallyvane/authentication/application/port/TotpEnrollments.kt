package tallyvane.authentication.application.port

import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId

/**
 * Where each account's TOTP enrolment is kept (ADR-093): at most one per account.
 *
 * The enrolment is kept apart from the recovery codes that go with it ([RecoveryCodeSets]), and the two
 * are changed together, under the lock [lock] takes. All methods run inside the caller's transaction
 * (`TransactionRunner`, ADR-052).
 *
 * ### Two requests at once cannot both use one code
 *
 * An enrolment remembers the step of the last code it accepted, and a second request that read the same
 * enrolment before the first saved would accept the same code again. So whoever is about to change the
 * enrolment reads it with [lock]: the second request waits there until the first commits and then reads
 * what the first saved. A plain [find] is for readers that change nothing.
 */
public interface TotpEnrollments {
    /**
     * The enrolment of [account], or null when it has none.
     *
     * @throws IllegalStateException if what is kept could not have been written by [TotpEnrollment.writeTo].
     */
    public fun find(account: AccountId): TotpEnrollment?

    /**
     * The enrolment of [account] as [find] reads it, and no other transaction can change it until this
     * one ends.
     */
    public fun lock(account: AccountId): TotpEnrollment?

    /**
     * Keeps [enrollment] as the one [account] has, replacing what it had.
     */
    public fun keep(account: AccountId, enrollment: TotpEnrollment)

    /**
     * Forgets the enrolment of [account] and, with it, the recovery codes that went with it. Forgetting
     * one that is not kept changes nothing.
     */
    public fun forget(account: AccountId)
}
