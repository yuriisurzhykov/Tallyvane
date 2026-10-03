package tallyvane.authentication.infrastructure

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.Secret
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Real Tink, a generated keyset and real AES-256-GCM, no mock of the library, so a change to how this
 * class calls Tink is still exercised for real.
 */
class TinkSecretCipherSpec :
    StringSpec(
        {
            val plain = Secret("JBSWY3DPEHPK3PXP")

            "a value survives sealing and opening unchanged" {
                val cipher = TinkSecretCipher(keyset())

                cipher.open(cipher.seal(plain)) shouldBe plain
            }

            "sealing the same value twice gives two different texts" {
                val cipher = TinkSecretCipher(keyset())

                cipher.seal(plain) shouldNotBe cipher.seal(plain)
            }

            "a text sealed under another keyset is refused, not opened to something wrong" {
                val sealedByOther = TinkSecretCipher(keyset()).seal(plain)

                shouldThrow<GeneralSecurityException> { TinkSecretCipher(keyset()).open(sealedByOther) }
            }

            "a text that was changed fails to authenticate instead of opening to garbage" {
                val cipher = TinkSecretCipher(keyset())

                shouldThrow<GeneralSecurityException> { cipher.open(withLastBitFlipped(cipher.seal(plain))) }
            }

            "the sealed text does not contain the value" {
                val sealed = TinkSecretCipher(keyset()).seal(plain)

                sealed.contains(plain.revealed()) shouldBe false
            }
        },
    )

private fun keyset(): Secret {
    AeadConfig.register()
    val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
    return Secret(TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get()))
}

private fun withLastBitFlipped(base64: String): String {
    val bytes = Base64.getDecoder().decode(base64)
    bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
    return Base64.getEncoder().encodeToString(bytes)
}
