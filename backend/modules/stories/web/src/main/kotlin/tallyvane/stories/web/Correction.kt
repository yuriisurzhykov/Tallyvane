package tallyvane.stories.web

import kotlinx.serialization.Serializable

/**
 * The person's own words correcting one fact of one story.
 */
@Serializable
internal class Correction(val story: String, val fact: String, val words: String)
