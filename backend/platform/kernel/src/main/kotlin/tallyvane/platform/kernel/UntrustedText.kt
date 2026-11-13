package tallyvane.platform.kernel

/**
 * Something a person typed or pasted, made safe to put in front of the model.
 *
 * Three things keep typed text from acting as instructions, and this is the first. It drops control and
 * invisible characters (the usual carriers of hidden instructions) and bounds the length. The second is that
 * a spec sends the text as a JSON string under a key its instructions call data. The third is what makes the
 * other two enough: the backend accepts nothing from a reply that is not an exact fragment of this text.
 */
public class UntrustedText private constructor(private val text: String) {
    /**
     * The text, as it is stored and as it is sent.
     */
    public fun cleaned(): String = text

    override fun equals(other: Any?): Boolean = other is UntrustedText && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "UntrustedText(${text.length} characters)"

    public companion object {
        /**
         * The longest text a person may send in one go; a long story told aloud is a tenth of it.
         */
        public const val LIMIT: Int = 20_000

        private val INVISIBLE = Regex("""[\p{Cc}\p{Cf}\p{Co}\p{Cn}&&[^\n\t]]""")

        /**
         * [raw] without invisible characters and outer blanks; null for nothing left, or for text longer
         * than [LIMIT].
         */
        public fun of(raw: String): UntrustedText? =
            INVISIBLE.replace(raw.replace("\r\n", "\n"), "").trim().takeIf { it.isNotEmpty() && it.length <= LIMIT }
                ?.let(::UntrustedText)
    }
}
