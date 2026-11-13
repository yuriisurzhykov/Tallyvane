package tallyvane.stories.web

import kotlinx.serialization.Serializable

/**
 * What the person said, and the story it adds to (null for a new one).
 */
@Serializable
internal class Telling(val words: String, val story: String? = null)
