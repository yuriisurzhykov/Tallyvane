package tallyvane.platform.llm

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import tallyvane.platform.kernel.Secret
import java.util.Base64

/**
 * Seals provider keys before they reach the database, and opens them only to call the provider.
 */
public class KeyCipher(keyset: Secret) {
    init {
        AeadConfig.register()
    }

    private val aead: Aead = TinkJsonProtoKeysetFormat
        .parseKeyset(keyset.revealed(), InsecureSecretKeyAccess.get())
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    internal fun seal(plain: Secret): String =
        Base64.getEncoder().encodeToString(aead.encrypt(plain.revealed().toByteArray(Charsets.UTF_8), CONTEXT))

    internal fun open(sealed: String): Secret =
        Secret(String(aead.decrypt(Base64.getDecoder().decode(sealed), CONTEXT), Charsets.UTF_8))

    override fun toString(): String = "KeyCipher"

    private companion object {
        val CONTEXT = "platform-model-provider-key-v1".toByteArray(Charsets.UTF_8)
    }
}
