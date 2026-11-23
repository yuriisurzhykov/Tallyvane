package tallyvane.stories.application.port

import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

/**
 * Where stories are kept. Every call runs inside a transaction the caller opened.
 */
public interface Stories {
    /**
     * The story [id], whoever it belongs to, or null.
     */
    public fun find(id: Uuid): Story?

    /**
     * Every story of [account], the newest first.
     */
    public fun of(account: Uuid): List<Story>

    /**
     * Keeps [story], replacing what was kept under its id.
     */
    public fun save(story: Story)
}
