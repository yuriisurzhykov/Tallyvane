package tallyvane.identity.infrastructure.secondfactor

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.platform.kernel.Secret
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Runs real Tink encryption and decryption — a generated keyset, real AES-256-GCM, no mock of the
 * library — so a change to how this class calls Tink is still exercised for real.
 */
class TinkSecretCipherSpec :
    StringSpec({
        val plaintext = Secret("JBSWY3DPEHPK3PXP")

        "a value survives an encrypt/decrypt round trip unchanged" {
            val cipher = TinkSecretCipher(freshKeyset())

            val decrypted = cipher.decrypt(cipher.encrypt(plaintext))

            decrypted shouldBe plaintext
        }

        "two encryptions of the same plaintext produce different ciphertexts" {
            val cipher = TinkSecretCipher(freshKeyset())

            val first = cipher.encrypt(plaintext)
            val second = cipher.encrypt(plaintext)

            first shouldNotBe second
        }

        "a ciphertext decrypted under a different key is rejected, not silently wrong" {
            val encryptedUnder = TinkSecretCipher(freshKeyset())
            val decryptedUnder = TinkSecretCipher(freshKeyset())

            shouldThrow<GeneralSecurityException> {
                decryptedUnder.decrypt(encryptedUnder.encrypt(plaintext))
            }
        }

        "a tampered ciphertext fails authentication rather than decrypting to garbage" {
            val cipher = TinkSecretCipher(freshKeyset())
            val genuine = cipher.encrypt(plaintext)
            val tampered = EncryptedSecret(flipLastByte(genuine.value))

            shouldThrow<GeneralSecurityException> { cipher.decrypt(tampered) }
        }
    })

/**
 * A keyset for the test to hand [TinkSecretCipher], generated the same way the composition
 * root's own one-time setup step would — not something [TinkSecretCipher] does itself, per this
 * class's own KDoc. Registers Tink's AEAD key managers first: [TinkSecretCipher]'s own `init`
 * block does this too, but only once constructed, and generating a keyset happens before that.
 */
private fun freshKeyset(): Secret {
    AeadConfig.register()
    val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
    return Secret(TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get()))
}

/**
 * Flips the low bit of the ciphertext's last byte — enough to break GCM's authentication tag
 * without producing a string [Base64] cannot decode.
 */
private fun flipLastByte(base64: String): String {
    val bytes = Base64.getDecoder().decode(base64)
    bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
    return Base64.getEncoder().encodeToString(bytes)
}
