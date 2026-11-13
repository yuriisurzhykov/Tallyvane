package tallyvane.stories.infrastructure

import tallyvane.platform.llm.core.Model
import tallyvane.stories.application.port.Reader
import tallyvane.stories.application.port.Stories
import tallyvane.stories.application.port.Utterances

/**
 * The adapters of the stories module, for the composition root.
 */
public class StoriesFactory {
    public fun stories(): Stories = PostgresStories()

    public fun utterances(): Utterances = PostgresUtterances()

    public fun reader(model: Model, skills: List<String>): Reader = ModelReader(model, skills)

    override fun toString(): String = "StoriesFactory(schema=stories)"
}
