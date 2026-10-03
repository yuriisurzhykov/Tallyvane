package tallyvane.authentication.application.port

import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.identity.contract.AccountId

/**
 * Where each account's recovery codes are kept (ADR-082, ADR-093): one set per account, and only for an
 * account that has a TOTP enrolment, since the set goes when the enrolment does.
 *
 * Changed under the lock [TotpEnrollments.lock] takes, so two requests that spend the same code are told
 * apart. Runs inside the caller's transaction (ADR-052).
 */
public interface RecoveryCodeSets {
    /**
     * The set of [account], or null when it has none.
     *
     * @throws IllegalStateException if what is kept could not have been written by [RecoveryCodes.writeTo].
     */
    public fun of(account: AccountId): RecoveryCodes?

    /**
     * Keeps [codes] as the whole set [account] has, replacing every code it had, spent or not.
     *
     * @throws IllegalStateException when [account] has no enrolment kept.
     */
    public fun keep(account: AccountId, codes: RecoveryCodes)
}
