package tallyvane.knowledge.domain

/** One line of a résumé, and the actions it speaks for. */
class Bullet(private val text: String, private val covers: List<ActionId>) {
    init {
        require(text.isNotBlank()) { "A bullet says something." }
        require(covers.isNotEmpty()) { "A bullet speaks for at least one action." }
    }

    fun writeTo(record: Record) = record.bullet(text, covers)

    interface Record {
        fun bullet(text: String, covers: List<ActionId>)
    }
}

/** Whether a bullet may stand, and if it may not, why, in words that can be given back to the model. */
class Verdict private constructor(private val problem: String?) {
    fun isAcceptable(): Boolean = problem == null

    fun why(): String = problem ?: "nothing is wrong"

    companion object {
        fun acceptable(): Verdict = Verdict(null)

        fun rejected(problem: String): Verdict = Verdict(problem)
    }
}

/**
 * The checks a model's bullet must pass before it is stored: short enough, no figure the person did not
 * give, and no name (technology, company, product) that is not in the facts of the project.
 */
class BulletCheck(
    private val technologies: TechnologyDictionary,
    private val maxWords: Int = MAX_WORDS,
    private val neutral: Set<String> = GENERIC_ACRONYMS,
) {
    fun verify(text: String, facts: ProjectFacts): Verdict {
        val words = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return Verdict.rejected("The bullet is empty.")
        if (words.size > maxWords) return Verdict.rejected("The bullet has ${words.size} words; the limit is $maxWords.")
        Numbers.written(text).firstOrNull { !facts.statesFigure(it) }?.let {
            return Verdict.rejected("The figure $it is not among the facts of the project.")
        }
        names(text).firstOrNull { !isKnownName(it, facts) }?.let {
            return Verdict.rejected("The name \"$it\" is not among the facts of the project.")
        }
        return Verdict.acceptable()
    }

    /**
     * A technology a bullet names must be one the project used, under any of its spellings: Kafka is a technology
     * the product knows, and it is still not a thing this person did if they never said so.
     */
    private fun isKnownName(word: String, facts: ProjectFacts): Boolean =
        word.uppercase() in neutral || facts.allowsName(word) || facts.allowsName(technologies.resolve(word).toString())

    /** The capitalised words that are not the first word of a sentence. */
    private fun names(text: String): List<String> =
        capitalised.findAll(text)
            .filterNot { match -> match.range.first == 0 || text.substring(0, match.range.first).trimEnd().lastOrNull() in SENTENCE_END }
            .map { it.value }
            .toList()

    private companion object {
        const val MAX_WORDS = 35
        val SENTENCE_END = setOf('.', '!', '?')
        val capitalised = Regex("""\p{Lu}[\p{L}\p{N}+#]*""")
        val GENERIC_ACRONYMS = setOf("API", "SQL", "CPU", "RAM", "UI", "UX", "QA", "CI", "CD", "ETL", "SLA", "DB", "I", "A")
    }
}
