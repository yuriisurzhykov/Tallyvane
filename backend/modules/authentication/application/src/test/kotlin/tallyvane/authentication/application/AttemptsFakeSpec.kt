package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.Attempts] must pass.
 */
class AttemptsFakeSpec : AttemptsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val attempts = AttemptsFake()
        override val transactions = TransactionRunnerFake()
    }
}
