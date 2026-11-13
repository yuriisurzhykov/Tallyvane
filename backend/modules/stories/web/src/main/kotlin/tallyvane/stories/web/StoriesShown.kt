package tallyvane.stories.web

import kotlinx.serialization.Serializable

@Serializable
internal class StoriesShown(val stories: List<StoryShown>)
