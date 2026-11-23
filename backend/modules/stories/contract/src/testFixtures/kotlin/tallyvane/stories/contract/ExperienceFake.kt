package tallyvane.stories.contract

import kotlin.uuid.Uuid

/**
 * [Experience] that tells every account the same stories.
 */
class ExperienceFake(private val stories: List<ToldStory> = emptyList()) : Experience {
    override suspend fun of(account: Uuid): List<ToldStory> = stories

    override fun toString(): String = "ExperienceFake(${stories.size})"
}
