package tallyvane.authentication.application

import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret

/**
 * Keys for suites that need attempts kept under more than one: a digest of a word, under a pepper no
 * production run uses.
 */
class KeysForTests {
    private val digests = Digests.Hmac(Secret("a-pepper-only-the-tests-use-0123456789"), 1)

    fun of(word: String): Digest = digests.of(Secret(word))

    override fun toString(): String = "KeysForTests"
}
