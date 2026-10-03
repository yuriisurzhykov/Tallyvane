package tallyvane.authentication.application

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Instant

/**
 * A person's authenticator app, written again from RFC 6238 and independent of the code under test: given
 * the key it was shown, it says what code to type at a moment.
 */
class AuthenticatorApp(key: String) {
    private val secret = decode(key)

    /**
     * The six digits the app shows at [time].
     */
    fun codeAt(time: Instant): String {
        val counter = time.epochSeconds / STEP_SECONDS
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteArray(Long.SIZE_BYTES) { index -> (counter shr (56 - 8 * index)).toByte() })
        val offset = hash.last().toInt() and 0x0f
        val number = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (number % MODULUS).toString().padStart(DIGITS, '0')
    }

    private fun decode(text: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var buffer = 0
        var bits = 0
        for (char in text.trimEnd('=')) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(char)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                bytes += ((buffer shr bits) and 0xff).toByte()
            }
        }
        return bytes.toByteArray()
    }

    override fun toString(): String = "AuthenticatorApp"

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        const val STEP_SECONDS = 30L
        const val DIGITS = 6
        const val MODULUS = 1_000_000
    }
}
