package tallyvane.sessions.domain

/**
 * The name a person gave one of their devices, such as "Work laptop".
 *
 * One to sixty characters once trimmed, without control characters: it is shown back in a list and
 * written into a security journal (ADR-083), so what it can hold is limited to what can be shown safely.
 */
public class DeviceName(text: String) {
    private val text: String = text.trim()

    init {
        require(accepts(this.text)) {
            "A device name is $MIN_LENGTH to $MAX_LENGTH characters without control characters."
        }
    }

    /**
     * Tells [record] the name.
     */
    public fun writeTo(record: Record) {
        record.name(text)
    }

    override fun equals(other: Any?): Boolean = other is DeviceName && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "DeviceName"

    /**
     * Whoever keeps the name.
     */
    public fun interface Record {
        public fun name(text: String)
    }

    public companion object {
        private const val MIN_LENGTH = 1
        private const val MAX_LENGTH = 60

        /**
         * Whether [text], after trimming, is a name a device may have.
         */
        public fun accepts(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.length in MIN_LENGTH..MAX_LENGTH && trimmed.none { it.isISOControl() }
        }
    }
}
