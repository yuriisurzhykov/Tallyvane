package tallyvane.identity.application.secondfactor.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * RFC 6238's TOTP function over RFC 4226's HOTP truncation — HMAC-SHA1, [digits] decimal digits,
 * a fresh code every [period]. The defaults match every value the `otpauth://` Key URI Format
 * leaves optional, so an authenticator app that assumes its own defaults still agrees with this
 * class.
 *
 * A pure function of `(secret, time)`: no port, no state, no ambient clock read — the caller
 * supplies [Instant] itself, so [Rfc6238TotpSpec] can check this against RFC 6238's own published
 * test vectors without needing to fake anything.
 */
internal class Rfc6238Totp(private val digits: Int = DEFAULT_DIGITS, private val period: Duration = DEFAULT_PERIOD) {
    /**
     * The code valid at [time] for [secret] — [secret]'s raw bytes, already decoded from
     * whatever text form stored it.
     */
    fun codeAt(secret: ByteArray, time: Instant): String {
        val step = time.epochSeconds / period.inWholeSeconds
        return hotp(secret, step)
    }

    /**
     * RFC 4226 §5.3: HMAC-SHA1 over the 8-byte big-endian counter, then dynamic truncation to
     * [digits] decimal digits.
     */
    private fun hotp(secret: ByteArray, counter: Long): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret, ALGORITHM))
        val hash = mac.doFinal(counterBytes(counter))

        val offset = hash[hash.size - 1].toInt() and LOW_NIBBLE
        val truncated =
            ((hash[offset].toInt() and HIGH_BYTE_MASK) shl BYTE_3) or
                ((hash[offset + 1].toInt() and BYTE_MASK) shl BYTE_2) or
                ((hash[offset + 2].toInt() and BYTE_MASK) shl BYTE_1) or
                (hash[offset + 3].toInt() and BYTE_MASK)

        return (truncated % TEN.pow(digits)).toString().padStart(digits, '0')
    }

    private fun counterBytes(counter: Long): ByteArray {
        val bytes = ByteArray(Long.SIZE_BYTES)
        var value = counter
        for (index in bytes.indices.reversed()) {
            bytes[index] = (value and BYTE_MASK.toLong()).toByte()
            value = value shr Byte.SIZE_BITS
        }
        return bytes
    }

    private fun Int.pow(exponent: Int): Int {
        var result = 1
        repeat(exponent) { result *= this }
        return result
    }

    private companion object {
        const val ALGORITHM = "HmacSHA1"
        const val DEFAULT_DIGITS = 6
        val DEFAULT_PERIOD = 30.seconds
        const val LOW_NIBBLE = 0x0f
        const val HIGH_BYTE_MASK = 0x7f
        const val BYTE_MASK = 0xff
        const val BYTE_1 = 8
        const val BYTE_2 = 16
        const val BYTE_3 = 24
        const val TEN = 10
    }
}
