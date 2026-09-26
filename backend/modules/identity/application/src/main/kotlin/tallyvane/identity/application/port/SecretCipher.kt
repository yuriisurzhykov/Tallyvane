package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.platform.kernel.Secret

/**
 * Encrypts a secret that must be read back, unlike a password or token, which never is —
 * TOTP's own seed is the one caller today. Reversible on purpose: computing a code from a stored
 * seed needs the raw value, which is exactly what [tallyvane.identity.application.port.PasswordHasher]
 * and [tallyvane.identity.application.port.TokenHasher] are built to never allow.
 *
 * [decrypt] throws rather than answering an outcome for a ciphertext that fails to authenticate —
 * unlike a wrong password, that is not an expected branch of normal control flow: it means a
 * corrupted row, a key rotated without re-encrypting, or tampering, and [tallyvane.identity.application.secondfactor]
 * has no policy for any of those to decide between.
 */
public interface SecretCipher {
    public fun encrypt(plaintext: Secret): EncryptedSecret

    public fun decrypt(ciphertext: EncryptedSecret): Secret
}
