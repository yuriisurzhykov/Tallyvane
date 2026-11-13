package tallyvane.career.application

import tallyvane.stories.contract.ToldStory

/**
 * Where a person stands on each skill, from their stories alone (0 tokens).
 */
public class Standing(private val stories: List<ToldStory>) {
    public fun of(skill: String): Level {
        val backing = stories.flatMap { story -> story.facts.filter { skill in it.skills }.map { story to it } }
        val resulted = backing.any { (story, _) -> story.facts.any { it.kind == OUTCOME } }
        return when {
            backing.size >= CONFIRMING && resulted -> Level.Confirmed
            backing.isNotEmpty() -> Level.Growing
            else -> Level.Gap
        }
    }

    /**
     * How many facts back [skill].
     */
    public fun evidence(skill: String): Int = stories.sumOf { story -> story.facts.count { skill in it.skills } }

    override fun toString(): String = "Standing(${stories.size})"

    private companion object {
        const val CONFIRMING = 2
        const val OUTCOME = "outcome"
    }
}
