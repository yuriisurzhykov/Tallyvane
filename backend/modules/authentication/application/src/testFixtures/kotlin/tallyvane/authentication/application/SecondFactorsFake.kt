package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId

/**
 * What is kept about second factors, in maps, for tests of the code that uses the ports (ADR-044).
 *
 * One class stands behind two ports because Postgres does: the recovery codes of an account go when its
 * enrolment does, and keeping that rule here spares the tests of every use case from restating it. Each
 * port is still held to its own suite, by [SecondFactorsFakeSpec].
 */
class SecondFactorsFake :
    TotpEnrollments,
    RecoveryCodeSets {
    private val enrollments = mutableMapOf<AccountId, TotpEnrollment>()
    private val sets = mutableMapOf<AccountId, RecoveryCodes>()

    override fun find(account: AccountId): TotpEnrollment? = enrollments[account]

    override fun lock(account: AccountId): TotpEnrollment? = enrollments[account]

    override fun keep(account: AccountId, enrollment: TotpEnrollment) {
        enrollments[account] = enrollment
    }

    override fun forget(account: AccountId) {
        enrollments.remove(account)
        sets.remove(account)
    }

    override fun keep(account: AccountId, codes: RecoveryCodes) {
        check(account in enrollments) { "Recovery codes are kept for an account that has an enrolment kept first." }
        sets[account] = codes
    }

    override fun of(account: AccountId): RecoveryCodes? = sets[account]

    override fun toString(): String = "SecondFactorsFake(${enrollments.size})"
}
