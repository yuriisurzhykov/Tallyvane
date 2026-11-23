package tallyvane.stories.domain

import kotlin.uuid.Uuid

/**
 * One thing a person said about their work, with the quotes that prove they said it.
 *
 * Never changed: a correction makes a new fact and marks this one as replaced by it.
 */
public class Fact private constructor(
    private val id: Uuid,
    private val kind: FactKind,
    private val text: String,
    private val quotes: List<Quote>,
    private val figures: List<Double>,
    private val technologies: List<String>,
    private val skills: List<String>,
    private val mine: Boolean,
    private val replacedBy: Uuid?,
) {
    init {
        require(text.isNotBlank()) { "A fact says something." }
        require(quotes.isNotEmpty()) { "A fact without a quote does not exist." }
        require(figures.all { figure -> quotes.any { it.backs(figure) } }) { "A figure is written in a quote." }
    }

    public fun isLive(): Boolean = replacedBy == null

    public fun has(id: Uuid): Boolean = this.id == id

    public fun says(text: String): Boolean = this.text.equals(text, ignoreCase = true)

    /**
     * The slot of a story this fact fills, or null.
     */
    public fun fills(): Slot? = when (kind) {
        FactKind.Context -> Slot.System
        FactKind.Action, FactKind.Decision -> Slot.Role.takeIf { mine }
        FactKind.Outcome -> Slot.Outcome
        FactKind.Scale -> Slot.Scale
        FactKind.People, FactKind.Learning -> null
    }

    /**
     * The quote of this fact that contains [part], narrowed to it, or null.
     */
    public fun quoteHolding(part: String): Quote? = quotes.firstOrNull { it.contains(part) }?.narrowedTo(part)

    /**
     * This fact, replaced by the fact [other].
     */
    public fun replacedBy(other: Uuid): Fact = Fact(id, kind, text, quotes, figures, technologies, skills, mine, other)

    public fun writeTo(record: Record) {
        record.fact(id, kind, text, figures, technologies, skills, mine, replacedBy)
        quotes.forEach { it.writeTo { utterance, fragment -> record.quote(id, utterance, fragment) } }
    }

    override fun equals(other: Any?): Boolean = other is Fact && other.id == id && other.replacedBy == replacedBy

    override fun hashCode(): Int = listOf(id, replacedBy).hashCode()

    override fun toString(): String = "Fact($kind, quotes=${quotes.size})"

    /**
     * Whoever keeps or shows a fact: told the fact, then each of its quotes.
     */
    public interface Record {
        @Suppress("LongParameterList") // A fact has this many parts, and a record is told all of them at once.
        public fun fact(
            id: Uuid,
            kind: FactKind,
            text: String,
            figures: List<Double>,
            technologies: List<String>,
            skills: List<String>,
            mine: Boolean,
            replacedBy: Uuid?,
        )

        public fun quote(fact: Uuid, utterance: Uuid, fragment: String)
    }

    public companion object {
        /**
         * A fact storage kept, or one a [Hearing] has just checked.
         *
         * @throws IllegalArgumentException it has no quote, or a figure no quote carries.
         */
        @Suppress("LongParameterList") // The parts of a fact, as storage kept them.
        public fun restore(
            id: Uuid,
            kind: FactKind,
            text: String,
            quotes: List<Quote>,
            figures: List<Double>,
            technologies: List<String>,
            skills: List<String>,
            mine: Boolean,
            replacedBy: Uuid?,
        ): Fact = Fact(id, kind, text, quotes, figures, technologies, skills, mine, replacedBy)
    }
}
