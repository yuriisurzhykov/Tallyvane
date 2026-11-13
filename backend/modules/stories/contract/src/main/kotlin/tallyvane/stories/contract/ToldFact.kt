package tallyvane.stories.contract

/**
 * One live fact as other modules see it. [kind] is lowercase: context, action, decision, outcome, scale, people,
 * learning.
 */
public class ToldFact(
    public val kind: String,
    public val text: String,
    public val skills: List<String>,
    public val mine: Boolean,
) {
    override fun toString(): String = "ToldFact($kind)"
}
