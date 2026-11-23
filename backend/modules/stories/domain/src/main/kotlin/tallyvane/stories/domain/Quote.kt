package tallyvane.stories.domain

import kotlin.uuid.Uuid

/**
 * An exact fragment of something a person said, and which utterance it is from.
 *
 * A fact without one does not exist. [Hearing.cite] makes it from the words themselves; [restore] is for
 * storage, which kept one that was made that way.
 */
public class Quote private constructor(private val utterance: Uuid, private val fragment: String) {
    /**
     * Whether the fragment carries [figure]: its digits are written in it, or it writes a number in words.
     *
     * A number in words ("полтора миллиона", "three hundred") is recognised as a number and its value is the
     * reader's; digits are compared exactly, with thousands read whether or not they are grouped.
     */
    public fun backs(figure: Double): Boolean {
        val digits = fragment.replace(GROUPING, "")
        val whole = figure.toLong()
        val written = if (figure == whole.toDouble()) whole.toString() else figure.toString()
        return NUMBER.findAll(digits).any { it.value.replace(',', '.').toDoubleOrNull() == written.toDouble() } ||
            (NUMBER.find(digits) == null && WORDS.containsMatchIn(fragment.lowercase()))
    }

    /**
     * Whether [other] is a part of this fragment, as a correction's unchanged part is.
     */
    public fun contains(other: String): Boolean = other.isNotBlank() && fragment.contains(other.trim())

    /**
     * The same utterance, a narrower fragment.
     */
    public fun narrowedTo(part: String): Quote = Quote(utterance, part.trim())

    public fun writeTo(record: Record) {
        record.quote(utterance, fragment)
    }

    override fun equals(other: Any?): Boolean =
        other is Quote && other.utterance == utterance && other.fragment == fragment

    override fun hashCode(): Int = listOf(utterance, fragment).hashCode()

    override fun toString(): String = "Quote(${fragment.length} characters)"

    /**
     * Whoever keeps or shows a quote.
     */
    public fun interface Record {
        public fun quote(utterance: Uuid, fragment: String)
    }

    public companion object {
        private val GROUPING = Regex("""(?<=\d)[ \u00A0\u202F,](?=\d{3}(?!\d))""")
        private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
        private val WORDS = Regex(
            "(?U)\\b(" +
                "ноль|нол[яь]|ни одн|один|одн[аоу]|дв[ауе]|двух|тр[иёе]|четыр|пят|шест|сем|восем|восьм|девят|десят|" +
                "сорок|сто\\b|ста\\b|сот|тысяч|миллион|миллиард|полтор|половин|" +
                "zero|none|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
                "twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|half|dozen)",
        )

        /**
         * A quote storage kept, or one [Hearing.cite] has just found in the words.
         */
        public fun restore(utterance: Uuid, fragment: String): Quote = Quote(utterance, fragment)
    }
}
