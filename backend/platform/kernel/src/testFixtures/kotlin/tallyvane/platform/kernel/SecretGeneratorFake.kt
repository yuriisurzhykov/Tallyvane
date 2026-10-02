package tallyvane.platform.kernel

/**
 * A [SecretGenerator] that yields `secret-1`, `secret-2`, … so a test can name the secret it expects
 * to find in a cookie or a lookup.
 *
 * Lives in `src/testFixtures`, so it never ships (ADR-044).
 */
class SecretGeneratorFake(private var sequence: Int = 0) : SecretGenerator {
    override fun next(): Secret {
        sequence += 1
        return Secret("secret-$sequence")
    }
}
