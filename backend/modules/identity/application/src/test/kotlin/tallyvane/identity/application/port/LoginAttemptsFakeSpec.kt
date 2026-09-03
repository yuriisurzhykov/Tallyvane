package tallyvane.identity.application.port

class LoginAttemptsFakeSpec : LoginAttemptsConformance() {
    override fun fresh(): LoginAttempts = LoginAttemptsFake()
}
