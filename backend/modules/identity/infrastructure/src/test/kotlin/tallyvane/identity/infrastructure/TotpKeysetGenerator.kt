package tallyvane.identity.infrastructure

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE

/** Explicit, one-shot operator utility. It refuses overwrite and never prints key material. */
public object TotpKeysetGenerator {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.size == 1) { "Pass one absolute output path." }
        val output = Path.of(args.single())
        require(output.isAbsolute) { "The output path must be absolute." }
        require(!Files.exists(output)) { "The output already exists; refusing to rotate an encryption key." }
        val parent = requireNotNull(output.parent) { "The output path needs a parent directory." }
        Files.createDirectories(parent)
        val privatePermissions = setOf(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
        )
        try {
            Files.createFile(output, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(privatePermissions))
        } catch (_: UnsupportedOperationException) {
            Files.createFile(output)
        }
        AeadConfig.register()
        val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        val serialized = TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())
        Files.newBufferedWriter(output, Charsets.UTF_8, WRITE).use { writer ->
            writer.write(serialized)
            writer.newLine()
        }
        println("TOTP encryption keyset created at the requested path. Protect and back it up securely.")
    }
}
