package tallyvane.knowledge.domain

/**
 * An exact piece of what the person said. It can only be obtained from the [Utterance] it was said in
 * (or restored from storage, where it was checked when it was stored), so a fact that holds a quote has
 * been shown to come from the person's own words.
 */
class Quote private constructor(private val text: String) {
    /** Whether [number] is written in this quote. A number that is not in the words was not said. */
    fun states(number: Double): Boolean = Numbers.written(text).any { it == number }

    fun writeTo(record: Record) = record.quote(text)

    interface Record {
        fun quote(text: String)
    }

    override fun equals(other: Any?): Boolean = other is Quote && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "\"$text\""

    companion object {
        internal fun of(text: String): Quote = Quote(text)

        /** For state that was already checked when it was stored (ADR-085). Not for anything fresh from a model. */
        fun restore(text: String): Quote = Quote(text)
    }
}
