package tallyvane.authentication.application

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.Google] must pass.
 */
class GoogleFakeSpec : GoogleConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = GoogleFake()
        override val google = fake

        override suspend fun codeFor(handshake: GoogleHandshake, subject: String, name: String, email: String): String =
            fake.arrange(stateOf(handshake), GoogleAnswer.Vouched(subject, GoogleProfile(name, email)))

        override suspend fun unverifiedCodeFor(handshake: GoogleHandshake, subject: String): String =
            fake.arrange(stateOf(handshake), GoogleAnswer.EmailUnverified())

        private fun stateOf(handshake: GoogleHandshake): String {
            val told = mutableListOf<String>()
            handshake.writeTo { state, _, _ -> told += state.revealed() }
            return told.single()
        }
    }
}
