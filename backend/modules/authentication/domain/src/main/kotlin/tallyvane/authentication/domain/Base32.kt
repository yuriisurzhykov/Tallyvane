package tallyvane.authentication.domain

/**
 * RFC 4648 base32 without padding: the alphabet an authenticator app reads a key in, and the one a
 * TOTP seed is kept in.
 *
 * Its own small class so that a spec can check it against the RFC's published vectors on its own.
 */
internal class Base32 {
    fun encode(bytes: ByteArray): String {
        val encoded = StringBuilder()
        var buffer = 0L
        var bitsInBuffer = 0
        for (byte in bytes) {
            buffer = (buffer shl BYTE_BITS) or (byte.toLong() and BYTE_MASK)
            bitsInBuffer += BYTE_BITS
            while (bitsInBuffer >= CHAR_BITS) {
                bitsInBuffer -= CHAR_BITS
                encoded.append(ALPHABET[((buffer shr bitsInBuffer) and CHAR_MASK).toInt()])
            }
        }
        if (bitsInBuffer > 0) {
            encoded.append(ALPHABET[((buffer shl (CHAR_BITS - bitsInBuffer)) and CHAR_MASK).toInt()])
        }
        return encoded.toString()
    }

    /**
     * The bytes [text] spells. Padding and case are accepted, anything else outside the alphabet is not.
     *
     * @throws IllegalArgumentException for a character that is not base32.
     */
    fun decode(text: String): ByteArray {
        val decoded = mutableListOf<Byte>()
        var buffer = 0L
        var bitsInBuffer = 0
        for (char in text.trimEnd(PADDING)) {
            val index = ALPHABET.indexOf(char.uppercaseChar())
            require(index >= 0) { "A character outside the base32 alphabet cannot be part of a seed." }
            buffer = (buffer shl CHAR_BITS) or index.toLong()
            bitsInBuffer += CHAR_BITS
            if (bitsInBuffer >= BYTE_BITS) {
                bitsInBuffer -= BYTE_BITS
                decoded += ((buffer shr bitsInBuffer) and BYTE_MASK).toByte()
            }
        }
        return decoded.toByteArray()
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        const val PADDING = '='
        const val BYTE_BITS = 8
        const val CHAR_BITS = 5
        const val BYTE_MASK = 0xFFL
        const val CHAR_MASK = 0x1FL
    }
}
