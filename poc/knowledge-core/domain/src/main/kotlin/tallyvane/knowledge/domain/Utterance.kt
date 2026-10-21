package tallyvane.knowledge.domain

/**
 * What the person typed in one answer. The only thing it is asked is whether a fragment a model claims to
 * have read in it is really there, which is the whole of the rule that nothing in a résumé is invented.
 */
class Utterance(private val text: String) {
    /** The fragment as a [Quote] if it appears in the answer exactly, character for character. */
    fun cite(fragment: String): Citation =
        if (fragment.isNotBlank() && text.contains(fragment)) {
            Citation.Quoted(Quote.of(fragment))
        } else {
            Citation.NotQuoted(fragment)
        }

    /** The whole answer as a quote, for facts that are the answer itself, such as "there is no metric". */
    fun asQuote(): Quote = Quote.of(text)

    fun writeTo(record: Record) = record.utterance(text)

    interface Record {
        fun utterance(text: String)
    }
}

/** Whether a fragment was found in an answer. */
sealed interface Citation {
    fun <T> reportTo(report: Report<T>): T

    interface Report<T> {
        fun quoted(quote: Quote): T

        fun notQuoted(fragment: String): T
    }

    class Quoted(private val quote: Quote) : Citation {
        override fun <T> reportTo(report: Report<T>): T = report.quoted(quote)
    }

    class NotQuoted(private val fragment: String) : Citation {
        override fun <T> reportTo(report: Report<T>): T = report.notQuoted(fragment)
    }
}
