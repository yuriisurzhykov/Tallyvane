package tallyvane.stories.application

import tallyvane.stories.application.port.Stories
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

/**
 * [Stories] in memory, for the tests of the code that keeps stories.
 */
class StoriesFake : Stories {
    private val kept = linkedMapOf<Uuid, Story>()

    override fun find(id: Uuid): Story? = kept[id]

    override fun of(account: Uuid): List<Story> = kept.values.filter { it.isOf(account) }.reversed()

    override fun save(story: Story) {
        kept.remove(story.id())
        kept[story.id()] = story
    }

    override fun toString(): String = "StoriesFake(${kept.size})"
}
