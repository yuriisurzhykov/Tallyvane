package tallyvane.identity.domain

/**
 * What a person is called on their own screens and in the documents made for them.
 *
 * Chosen by the person on the welcome screen, starting from the name Google reported. Trimmed, and
 * between one and [MAX_LENGTH] characters with no control characters: a name is text that is shown,
 * and a line break or a terminal escape in it is a way to break the page or the log that shows it.
 *
 * Only [of] makes one, so every display name that exists is one that was checked.
 */
public class DisplayName private constructor(private val value: String) {
    /**
     * Tells [record] the name, for the one place that stores it.
     */
    public fun writeTo(record: (String) -> Unit) {
        record(value)
    }

    override fun equals(other: Any?): Boolean = other is DisplayName && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "DisplayName(length=${value.length})"

    public companion object {
        /**
         * The longest name accepted. Long enough for any real name with a middle name or two, short
         * enough that a page laying it out does not have to plan for a paragraph.
         */
        public const val MAX_LENGTH: Int = 80

        /**
         * [raw] as a display name, trimmed; null for one that is empty, longer than [MAX_LENGTH] or
         * holds a control character.
         */
        public fun of(raw: String): DisplayName? = raw.trim()
            .takeIf { trimmed -> trimmed.length in 1..MAX_LENGTH && trimmed.none(Char::isISOControl) }
            ?.let(::DisplayName)
    }
}
