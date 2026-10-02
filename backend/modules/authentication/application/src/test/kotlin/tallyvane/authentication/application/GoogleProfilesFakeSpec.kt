package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.GoogleProfiles] must pass.
 */
class GoogleProfilesFakeSpec : GoogleProfilesConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = SignInsFake()
        override val attempts = fake
        override val profiles = fake
        override val transactions = TransactionRunnerFake()
    }
}
