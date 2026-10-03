package tallyvane.authentication.infrastructure

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import tallyvane.platform.kernel.Secret
import java.util.Base64

/**
 * [SecretCipher] with AES-256-GCM through Tink, over a keyset the deployment supplies and this class
 * never makes.
 *
 * Making a keyset is a one-time operational step (`KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)`
 * written with `TinkJsonProtoKeysetFormat.serializeKeyset`), kept out of any code that runs in production:
 * Tink's own guidance names mixing the making of a key with its use as the mistake to avoid.
 *
 * `AeadConfig.register()` runs once per instance because Tink finds a primitive through its global
 * registry; it is safe to call again.
 */
internal class TinkSecretCipher(serializedKeyset: Secret) : SecretCipher {
    init {
        AeadConfig.register()
    }

    private val aead: Aead = TinkJsonProtoKeysetFormat
        .parseKeyset(serializedKeyset.revealed(), InsecureSecretKeyAccess.get())
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    override fun seal(plain: Secret): String {
        val sealed = aead.encrypt(plain.revealed().toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.getEncoder().encodeToString(sealed)
    }

    override fun open(sealed: String): Secret {
        val plain = aead.decrypt(Base64.getDecoder().decode(sealed), ASSOCIATED_DATA)
        return Secret(String(plain, Charsets.UTF_8))
    }

    override fun toString(): String = "TinkSecretCipher"

    private companion object {
        /**
         * Binds every text this class seals to what it is for, so a value taken from some other purpose
         * under the same key does not open here.
         *
         * No dot in this literal on purpose: `own-schema-only` reads any `"lowercase.rest"` string in a
         * module's source as a claimed Postgres schema.
         */
        val ASSOCIATED_DATA = "authentication-totp-seed-v1".toByteArray(Charsets.UTF_8)
    }
}
