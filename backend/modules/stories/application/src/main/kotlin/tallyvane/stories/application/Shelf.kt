package tallyvane.stories.application

import tallyvane.stories.domain.Story

/**
 * Every story of one person, to show.
 */
public class Shelf internal constructor(private val stories: List<Story>) {
    public fun writeTo(record: Story.Record) {
        stories.forEach { it.writeTo(record) }
    }

    override fun toString(): String = "Shelf(${stories.size})"
}
