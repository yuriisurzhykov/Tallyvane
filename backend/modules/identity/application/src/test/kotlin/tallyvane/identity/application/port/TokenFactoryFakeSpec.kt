package tallyvane.identity.application.port

class TokenFactoryFakeSpec : TokenFactoryConformance() {
    override fun fresh(): TokenFactory = TokenFactoryFake()
}
