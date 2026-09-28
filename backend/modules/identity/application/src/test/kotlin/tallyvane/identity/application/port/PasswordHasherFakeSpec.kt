package tallyvane.identity.application.port

class PasswordHasherFakeSpec : PasswordHasherConformance() {
    override fun fresh(): PasswordHasher = PasswordHasherFake()
}
