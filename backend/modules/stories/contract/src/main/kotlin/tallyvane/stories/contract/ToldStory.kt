package tallyvane.stories.contract

import kotlin.uuid.Uuid

/**
 * One story as other modules see it. [missing] is the slot still to be asked about, or null.
 */
@Suppress("LongParameterList") // What a story is, as other modules read it.
public class ToldStory(
    public val id: Uuid,
    public val title: String?,
    public val company: String?,
    public val position: String?,
    public val period: String?,
    public val missing: String?,
    public val facts: List<ToldFact>,
) {
    override fun toString(): String = "ToldStory($id, facts=${facts.size})"
}
