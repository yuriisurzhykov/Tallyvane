package tallyvane.stories.infrastructure

import kotlinx.serialization.Serializable

/**
 * What the model answers to `extract_facts@1`.
 */
@Serializable
internal class ModelAnswer(val place: ModelPlace = ModelPlace(), val facts: List<ModelFact> = emptyList())
