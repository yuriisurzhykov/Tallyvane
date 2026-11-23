package tallyvane.stories.domain

import tallyvane.platform.kernel.UntrustedText
import kotlin.uuid.Uuid

/**
 * One utterance being heard: the words, which utterance they are, and the skills the product knows.
 *
 * It is where the rule "nothing is invented" is held. A claim becomes a fact only if every fragment it rests
 * on is in the words (or, for a correction, in the quotes of the fact it corrects), every figure is written in
 * one of those fragments, and its skills are ones the catalog lists. Anything else is dropped, silently: the
 * reader was wrong, and the person is not asked to care.
 */
public class Hearing(private val words: UntrustedText, private val utterance: Uuid, private val known: Set<String>) {
    /**
     * The exact [fragment] of the words as a quote, or null when the words do not contain it.
     */
    public fun cite(fragment: String): Quote? = fragment.trim()
        .takeIf { it.length >= SHORTEST && words.cleaned().contains(it) }
        ?.let { Quote.restore(utterance, it) }

    /**
     * Whether the person said they have nothing to add: a few words, one of them a refusal.
     */
    public fun saysNothingMore(): Boolean {
        val said = words.cleaned().lowercase()
        return said.split(BLANK).size <= FEW && NOTHING.containsMatchIn(said)
    }

    /**
     * [claim] as the fact [id], or null when it does not hold. [corrected] is the fact being corrected, whose
     * quotes may back the parts of the claim the correction left as they were.
     */
    public fun factOf(claim: Claim, id: Uuid, corrected: Fact? = null): Fact? {
        val quotes = claim.fragments.map { fragment -> cite(fragment) ?: corrected?.quoteHolding(fragment) }
        val fresh = claim.fragments.any { cite(it) != null }
        val text = claim.text.trim().take(LONGEST)
        val backed = claim.figures.all { figure -> quotes.filterNotNull().any { it.backs(figure) } }
        val holds = listOf(text.isNotEmpty(), quotes.isNotEmpty(), null !in quotes, fresh, backed).all { it }
        return if (!holds) {
            null
        } else {
            Fact.restore(
                id,
                claim.kind,
                text,
                quotes.filterNotNull().distinct(),
                claim.figures.distinct(),
                claim.technologies.map { it.trim().take(NAME) }.filter { it.isNotEmpty() }.distinct().take(MANY),
                claim.skills.filter { it in known }.distinct().take(TAGS),
                claim.mine,
                null,
            )
        }
    }

    override fun toString(): String = "Hearing($utterance)"

    private companion object {
        const val SHORTEST = 3
        const val LONGEST = 300
        const val NAME = 40
        const val MANY = 8
        const val TAGS = 3
        const val FEW = 5
        val BLANK = Regex("""\s+""")
        val NOTHING = Regex(
            "(^|[^\\p{L}])(нет|не было|ничего|больше нечего|это вс[её]|вс[её]|no|nope|nothing|none|that's all|" +
                "that is all)([^\\p{L}]|$)",
        )
    }
}
