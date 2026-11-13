package tallyvane.stories.domain

/**
 * Where a story happened: the company, the position, and from when to when. Any part may be unknown.
 */
public class Place(
    private val company: String?,
    private val position: String?,
    private val started: String?,
    private val ended: String?,
) {
    /**
     * This place with its unknown parts taken from [other]. What is already known is never overwritten: the
     * person said it first, and a later reading does not get to change it.
     */
    public fun filledFrom(other: Place): Place = Place(
        company ?: other.company,
        position ?: other.position,
        started ?: other.started,
        ended ?: other.ended,
    )

    public fun writeTo(record: Record) {
        record.place(clean(company), clean(position), period(started), period(ended))
    }

    private fun clean(name: String?): String? = name?.trim()?.take(LONGEST)?.takeIf { it.isNotEmpty() }

    private fun period(written: String?): String? = written?.trim()?.takeIf { PERIOD.matches(it) }

    override fun equals(other: Any?): Boolean = other is Place &&
        other.company == company &&
        other.position == position &&
        other.started == started &&
        other.ended == ended

    override fun hashCode(): Int = listOf(company, position, started, ended).hashCode()

    override fun toString(): String = "Place(known=${listOfNotNull(company, position, started, ended).size})"

    /**
     * Whoever keeps or shows a place. A period is told as `2019` or `2019-03`, or not at all.
     */
    public fun interface Record {
        public fun place(company: String?, position: String?, started: String?, ended: String?)
    }

    private companion object {
        const val LONGEST = 120
        val PERIOD = Regex("""\d{4}(-(0[1-9]|1[0-2]))?""")
    }
}
