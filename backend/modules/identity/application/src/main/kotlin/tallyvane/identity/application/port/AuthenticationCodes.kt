package tallyvane.identity.application.port

import tallyvane.platform.kernel.Secret
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Purpose-separated keyed hashing protects low-entropy email codes even if the database leaks.
 */
public interface AuthenticationCodes {
    public fun emailCode(): Secret

    public fun backupCode(): Secret

    public fun hash(context: String, code: Secret): Secret

    public class Hmac(private val pepper: Secret) : AuthenticationCodes {
        private val random = SecureRandom()

        init {
            require(pepper.revealed().toByteArray().size >= MIN_PEPPER_BYTES) {
                "Code pepper must contain at least $MIN_PEPPER_BYTES bytes"
            }
        }

        override fun emailCode(): Secret = Secret(
            random.nextInt(EMAIL_CODE_RANGE).toString().padStart(EMAIL_CODE_LENGTH, '0'),
        )

        override fun backupCode(): Secret {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return Secret(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }

        override fun hash(context: String, code: Secret): Secret {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(pepper.revealed().toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return Secret(
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal("$context\u0000${code.revealed()}".toByteArray(Charsets.UTF_8)),
                ),
            )
        }

        private companion object {
            const val MIN_PEPPER_BYTES = 32
            const val EMAIL_CODE_RANGE = 1_000_000
            const val EMAIL_CODE_LENGTH = 6
        }
    }
}
