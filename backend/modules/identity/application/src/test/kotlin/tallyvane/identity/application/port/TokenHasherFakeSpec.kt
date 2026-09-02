package tallyvane.identity.application.port

class TokenHasherFakeSpec : TokenHasherConformance() {
    override fun fresh(pepperVersion: Int): TokenHasher = TokenHasherFake(pepperVersion)
}
