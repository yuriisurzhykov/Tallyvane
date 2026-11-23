package tallyvane.knowledge.domain

enum class Direction(private val wire: String) {
    UP("up"),
    DOWN("down"),
    ;

    fun wireName(): String = wire

    companion object {
        fun isKnown(raw: String): Boolean = entries.any { it.wire == raw }

        fun named(raw: String): Direction =
            entries.firstOrNull { it.wire == raw } ?: throw IllegalArgumentException("Not a direction: $raw")
    }
}

/**
 * A result the person reported. If it carries a number, that number is written in its [Quote]: an outcome
 * whose figure the person did not say cannot be built.
 */
class Outcome(
    private val what: String,
    private val direction: Direction?,
    private val value: Double?,
    private val unit: String?,
    private val approx: Boolean,
    private val quote: Quote,
) {
    init {
        require(what.isNotBlank()) { "An outcome is about something." }
        require(value == null || quote.states(value)) { "The figure $value is not in the quote $quote." }
    }

    /** Whether the result is backed by a number. */
    fun hasMetric(): Boolean = value != null

    /** The figure written as a person would write it: `40`, `0.5`. Nothing for an outcome without one. */
    fun statesFigure(figure: Double): Boolean = value == figure

    /** How a bullet says it: `reducing database load by about 40%`. */
    fun phrase(): String {
        val verb =
            when (direction) {
                Direction.DOWN -> "reducing"
                Direction.UP -> "increasing"
                null -> null
            }
        val amount = value?.let { (if (approx) "about " else "") + figure(it) + (unit ?: "") }
        return when {
            verb != null && amount != null -> "$verb $what by $amount"
            amount != null -> "$what: $amount"
            else -> what
        }
    }

    fun writeTo(record: Record) = record.outcome(what, direction, value, unit, approx, hasMetric(), quote)

    interface Record {
        fun outcome(
            what: String,
            direction: Direction?,
            value: Double?,
            unit: String?,
            approx: Boolean,
            hasMetric: Boolean,
            quote: Quote,
        )
    }

    companion object {
        /** The person said there is no number for the result; their answer is the evidence. */
        fun unmeasured(answer: Quote): Outcome = Outcome("result not measured", null, null, null, false, answer)

        private fun figure(value: Double): String =
            if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
    }
}
