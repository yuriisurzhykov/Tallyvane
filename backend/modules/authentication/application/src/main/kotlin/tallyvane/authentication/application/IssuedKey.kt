package tallyvane.authentication.application

import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret

/**
 * A secret just handed to a browser, and the key its attempt is kept under. Only this module reads
 * either; the secret leaves it inside an outcome, on its way to the cookie.
 */
public class IssuedKey internal constructor(internal val secret: Secret, internal val key: Digest) {
    override fun toString(): String = "IssuedKey($key)"
}
