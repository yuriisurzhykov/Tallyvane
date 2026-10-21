package tallyvane.knowledge.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * When a person held a role, as they said it: `2022`, or `2019-06`. Either end may be unknown, and a role
 * is either current, over, or not yet said to be either.
 */
class RolePeriod(
    private val start: String?,
    private val end: String?,
    private val current: Boolean?,
) {
    /** Whether the person has not yet said when it began. */
    fun lacksStart(): Boolean = start == null

    /** Whether the person has not yet said if it is over or still going. */
    fun lacksEndOrCurrent(): Boolean = end == null && current == null

    /** This period with the parts of [said] it did not know, and none of the parts it did. */
    fun completedBy(said: RolePeriod): RolePeriod =
        RolePeriod(start ?: said.start, end ?: said.end, current ?: said.current)

    /** `2022 - present`, `2019 - 2022`, or only what is known. */
    fun label(): String {
        val to = if (current == true) "present" else end
        return when {
            start != null && to != null -> "$start – $to"
            start != null -> "since $start"
            to != null -> "until $to"
            else -> ""
        }
    }

    /**
     * How recent the role is on [today] for ranking: 1 while it is current, then 0.1 less for each year since
     * it ended, never under 0.4. A role with no known end gets no penalty; an unknown date is not a date.
     */
    fun recencyOn(today: LocalDate): Double {
        if (current == true) return 1.0
        val ended = endDate() ?: return 1.0
        val years = java.time.temporal.ChronoUnit.DAYS.between(ended, today) / DAYS_PER_YEAR
        return (1.0 - YEARLY_LOSS * years.coerceAtLeast(0.0)).coerceAtLeast(FLOOR)
    }

    /** A number that is larger for a role that is more recent, to put roles in reverse chronological order. */
    fun recencyRank(): Int =
        when {
            current == true -> Int.MAX_VALUE
            else -> (endMonth() ?: startMonth())?.let { it.year * MONTHS + it.monthValue } ?: 0
        }

    private fun endDate(): LocalDate? = endMonth()?.atEndOfMonth() ?: end?.let { year(it) }?.let { LocalDate.of(it, 12, 31) }

    private fun endMonth(): YearMonth? = end?.let { month(it) }

    private fun startMonth(): YearMonth? = start?.let { month(it) ?: year(it)?.let { y -> YearMonth.of(y, 1) } }

    private fun month(text: String): YearMonth? =
        Regex("""^(\d{4})-(\d{2})$""").find(text.trim())?.let {
            YearMonth.of(it.groupValues[1].toInt(), it.groupValues[2].toInt().coerceIn(1, MONTHS))
        }

    private fun year(text: String): Int? = Regex("""\b(\d{4})\b""").find(text)?.groupValues?.get(1)?.toInt()

    fun writeTo(record: Record) = record.period(start, end, current)

    interface Record {
        fun period(start: String?, end: String?, current: Boolean?)
    }

    private companion object {
        const val DAYS_PER_YEAR = 365.25
        const val YEARLY_LOSS = 0.1
        const val FLOOR = 0.4
        const val MONTHS = 12
    }
}
