package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.Enrollment
import tallyvane.identity.contract.AccountId

/**
 * What an account has set up, read from where each factor is kept.
 *
 * Runs inside the caller's transaction, as the ports it reads do.
 */
public class Enrollments(private val totp: TotpEnrollments, private val codes: RecoveryCodeSets) {
    /**
     * The factors [account] has set up and confirmed.
     */
    public fun of(account: AccountId): Enrollment = Enrollment.of(totp.find(account), codes.of(account))

    override fun toString(): String = "Enrollments(totp=$totp, codes=$codes)"
}
