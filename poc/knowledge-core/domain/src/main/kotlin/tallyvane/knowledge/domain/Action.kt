package tallyvane.knowledge.domain

/** What kind of thing a person did. The names are the wire form the model must use and the database stores. */
enum class ActionKind(private val wire: String) {
    INVESTIGATED("investigated"),
    DESIGNED("designed"),
    DECIDED("decided"),
    BUILT("built"),
    COMMUNICATED("communicated"),
    LED("led"),
    MENTORED("mentored"),
    IMPROVED("improved"),
    OTHER("other"),
    ;

    fun wireName(): String = wire

    companion object {
        fun isKnown(raw: String): Boolean = entries.any { it.wire == raw }

        fun named(raw: String): ActionKind =
            entries.firstOrNull { it.wire == raw } ?: throw IllegalArgumentException("Not an action kind: $raw")
    }
}

/**
 * One thing the person did, in the person's words as the model restated them in English: a verb, what it
 * was done to, and, when they said what they found out, the finding. It holds the [Quote] it came from.
 */
class Action(
    private val kind: ActionKind,
    private val verb: String,
    private val what: String,
    private val finding: String?,
    private val quote: Quote,
) {
    init {
        require(verb.isNotBlank()) { "An action has a verb." }
        require(what.isNotBlank()) { "An action is done to something." }
    }

    fun isOfKind(other: ActionKind): Boolean = kind == other

    /** The text whose meaning is compared with other actions and with the skills. */
    fun embeddingText(): String = "$verb $what"

    /** The verb with a capital letter, as a résumé bullet begins. */
    fun beginsSentence(): String = verb.replaceFirstChar { it.uppercase() }

    fun describesWhat(): String = what

    /** Whether the finding the person reported (`~70% were repeated reads`) mentions [figure]. */
    fun findingStates(figure: Double): Boolean = finding != null && Numbers.written(finding).any { it == figure }

    fun writeTo(record: Record) = record.action(kind, verb, what, finding, quote)

    interface Record {
        fun action(kind: ActionKind, verb: String, what: String, finding: String?, quote: Quote)
    }
}
