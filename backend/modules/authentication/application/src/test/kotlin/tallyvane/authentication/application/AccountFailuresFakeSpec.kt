package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.AccountFailures] must pass.
 */
class AccountFailuresFakeSpec : AccountFailuresConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val failures = AccountFailuresFake()
        override val transactions = TransactionRunnerFake()
    }
}
