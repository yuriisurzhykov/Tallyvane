package tallyvane.authentication.domain

/**
 * Text as it is written in a URI component (RFC 3986): everything but unreserved characters becomes
 * `%XX` over its UTF-8 bytes.
 */
internal class PercentEncoding {
    /**
     * [text] with every byte outside `A-Z a-z 0-9 - . _ ~` written as a percent sign and two capital
     * hexadecimal digits.
     */
    fun of(text: String): String = text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
        val value = byte.toInt() and BYTE_MASK
        if (value.toChar() in UNRESERVED) value.toChar().toString() else "%%%02X".format(value)
    }

    override fun toString(): String = "PercentEncoding"

    private companion object {
        const val BYTE_MASK = 0xff

        val UNRESERVED: Set<Char> = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()
    }
}
