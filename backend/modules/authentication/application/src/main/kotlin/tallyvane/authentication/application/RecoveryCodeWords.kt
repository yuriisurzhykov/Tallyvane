package tallyvane.authentication.application

import tallyvane.platform.kernel.Secret

/**
 * How a recovery code a person typed becomes the one that was issued: case, dashes and spaces do not
 * matter, so `abcde fghjk` is `ABCDE-FGHJK`.
 */
public class RecoveryCodeWords {
    /**
     * The code in the one form its digest is made from.
     */
    public fun normalised(typed: String): Secret = Secret(typed.filter { it.isLetterOrDigit() }.uppercase())

    override fun toString(): String = "RecoveryCodeWords"
}
