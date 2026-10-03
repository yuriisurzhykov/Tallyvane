package tallyvane.authentication.domain

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * RFC 6238's TOTP over RFC 4226's HOTP truncation: HMAC-SHA1, [digits] decimal digits, a fresh code
 * every [period]. The defaults are the values an `otpauth://` address leaves optional, so an
 * authenticator app that assumes its own defaults agrees with this class.
 *
 * A pure function of the secret and the time: the caller supplies the [Instant], so a spec checks it
 * against the RFCs' published vectors without a clock to fake.
 */
internal class Rfc6238Totp(private val digits: Int = DEFAULT_DIGITS, private val period: Duration = DEFAULT_PERIOD) {
    /**
     * The code valid at [time] for [secret], the seed's raw bytes.
     */
    fun codeAt(secret: ByteArray, time: Instant): String = hotp(secret, stepAt(time))

    /**
     * The time step [time] falls in.
     */
    fun stepAt(time: Instant): Long = time.epochSeconds / period.inWholeSeconds

    /**
     * The step at which [code] is right for [secret], looking at the step of [time] and [tolerance] steps
     * either side, and only at steps later than [after] when there is one. Null when none matches.
     *
     * Every candidate is compared whether or not an earlier one matched, and by a comparison that does not
     * stop at the first difference, so how long this takes says nothing about how close a guess was.
     */
    fun matchingStep(secret: ByteArray, code: String, time: Instant, tolerance: Int, after: Long?): Long? {
        val now = stepAt(time)
        var matched: Long? = null
        for (step in (now - tolerance)..(now + tolerance)) {
            val candidate = hotp(secret, step).toByteArray(Charsets.US_ASCII)
            val same = MessageDigest.isEqual(candidate, code.toByteArray(Charsets.US_ASCII))
            if (same && (after == null || step > after)) {
                matched = step
            }
        }
        return matched
    }

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
