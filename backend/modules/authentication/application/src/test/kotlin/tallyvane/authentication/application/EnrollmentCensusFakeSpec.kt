package tallyvane.authentication.application

import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.EnrollmentCensus] must pass.
 */
class EnrollmentCensusFakeSpec : EnrollmentCensusConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = SecondFactorsFake()

        override val storage: SecondFactorStorage = object : SecondFactorStorage {
            override val enrollments = fake
            override val codes = fake
            override val transactions = TransactionRunnerFake()
        }
        override val census: EnrollmentCensus = EnrollmentCensusFake(fake)
    }
}
