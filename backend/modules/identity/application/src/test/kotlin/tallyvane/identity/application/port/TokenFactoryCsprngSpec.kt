package tallyvane.identity.application.port

class TokenFactoryCsprngSpec : TokenFactoryConformance() {
    override fun fresh(): TokenFactory = TokenFactory.Csprng()
}
