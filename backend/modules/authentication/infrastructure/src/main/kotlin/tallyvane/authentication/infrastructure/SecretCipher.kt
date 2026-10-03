package tallyvane.authentication.infrastructure

import tallyvane.platform.kernel.Secret

/**
 * Seals a secret that must be read back, unlike a password or a token, which never is: a TOTP seed is the
 * one caller, since computing a code needs the seed itself.
 *
 * [open] throws for what does not authenticate. That is not an expected outcome like a wrong password: it
 * means a damaged row, a keyset changed without re-sealing, or tampering, and nothing above this decides
 * between those.
 */
internal interface SecretCipher {
    /**
     * [plain] sealed as text that is safe to keep in a column. Sealing it twice gives two different texts.
     */
    fun seal(plain: Secret): String

    /**
     * What [seal] sealed.
     *
     * @throws java.security.GeneralSecurityException for text that was not sealed by this keyset.
     */
    fun open(sealed: String): Secret
}
