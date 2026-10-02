package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.GoogleHandshakes] must pass.
 */
class GoogleHandshakesFakeSpec : GoogleHandshakesConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = SignInsFake()
        override val attempts = fake
        override val handshakes = fake
        override val transactions = TransactionRunnerFake()
    }
}
