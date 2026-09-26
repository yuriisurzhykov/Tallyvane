package tallyvane.identity.application.secondfactor.totp

/**
 * RFC 4648 base32 — the encoding both the `secret` parameter of an `otpauth://` URI and this
 * module's own storage of a raw TOTP seed use, kept as its own small class so
 * [Base32Spec] can check it against the RFC's own published test vectors independently of
 * [tallyvane.identity.application.port.SecondFactorMethod]'s real behaviour.
 */
internal class Base32 {
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
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
        while (encoded.length % GROUP_CHARS != 0) {
            encoded.append(PADDING)
        }
        return encoded.toString()
    }

    fun decode(text: String): ByteArray {
        val decoded = mutableListOf<Byte>()
        var buffer = 0L
        var bitsInBuffer = 0
        for (char in text.trimEnd(PADDING)) {
            val index = ALPHABET.indexOf(char.uppercaseChar())
            require(index >= 0) { "'$char' is not a base32 character" }
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
        const val GROUP_CHARS = 8
        const val BYTE_MASK = 0xFFL
        const val CHAR_MASK = 0x1FL
    }
}
