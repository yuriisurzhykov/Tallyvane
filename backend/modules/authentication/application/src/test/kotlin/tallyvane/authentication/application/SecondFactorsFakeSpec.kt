package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.TotpEnrollments] and
 * [tallyvane.authentication.application.port.RecoveryCodeSets] must pass.
 */
class SecondFactorsFakeSpec : SecondFactorsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = SecondFactorsFake()
        override val enrollments = fake
        override val codes = fake
        override val transactions = TransactionRunnerFake()
    }
}
