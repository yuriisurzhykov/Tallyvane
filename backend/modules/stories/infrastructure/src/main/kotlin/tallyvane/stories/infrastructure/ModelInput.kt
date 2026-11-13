package tallyvane.stories.infrastructure

import kotlinx.serialization.Serializable

/**
 * What the model is given: the person's words as a JSON string, so they stay data (prompt-injection defence).
 */
@Serializable
internal class ModelInput(
    val words: String,
    val skills: List<String>,
    val asked: String? = null,
    val fact: ModelFact? = null,
)
