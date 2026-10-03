package tallyvane.authentication.application

import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * What a suite of the second-factor ports is given to run against: both ports over one store, since a
 * set of recovery codes belongs to an enrolment, and the transactions their callers open.
 */
interface SecondFactorStorage {
    val enrollments: TotpEnrollments
    val codes: RecoveryCodeSets
    val transactions: TransactionRunner

    /**
     * Runs [call] in a transaction of its own that commits.
     */
    suspend fun <T> inOwnTransaction(call: () -> T): T = transactions.inTransaction { Verdict.Commit(call()) }
}
