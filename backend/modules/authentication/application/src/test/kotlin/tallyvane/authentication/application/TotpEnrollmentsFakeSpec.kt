package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.TotpEnrollments] must pass.
 */
class TotpEnrollmentsFakeSpec : TotpEnrollmentsConformance() {
    override suspend fun fresh(): SecondFactorStorage = object : SecondFactorStorage {
        private val fake = SecondFactorsFake()
        override val enrollments = fake
        override val codes = fake
        override val transactions = TransactionRunnerFake()
    }
}
