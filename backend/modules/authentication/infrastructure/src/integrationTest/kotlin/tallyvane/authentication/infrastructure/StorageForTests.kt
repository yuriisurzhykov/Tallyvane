package tallyvane.authentication.infrastructure

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret

/**
 * A keyset made the way the one-time operational step makes it, for a specimen that needs something to
 * seal seeds with.
 */
fun freshKeyset(): Secret {
    AeadConfig.register()
    val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
    return Secret(TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get()))
}

/**
 * The storage a specimen runs against: ids from a fake and a keyset of its own.
 */
fun storageForTests(): AuthenticationStorageFactory = AuthenticationStorageFactory(IdGeneratorFake(), freshKeyset())
