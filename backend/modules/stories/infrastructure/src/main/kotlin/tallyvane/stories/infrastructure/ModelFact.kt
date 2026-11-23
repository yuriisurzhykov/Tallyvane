package tallyvane.stories.infrastructure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tallyvane.stories.domain.Claim
import tallyvane.stories.domain.FactKind

/**
 * One fact as the model writes it (`extract_facts@1`, `correct_fact@1`).
 */
@Serializable
internal class ModelFact(
    val kind: String,
    @SerialName("text_en") val text: String,
    val q: List<String> = emptyList(),
    val figures: List<Double> = emptyList(),
    val tech: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val mine: Boolean = false,
) {
    /**
     * The claim, or null when the kind is not one the product knows.
     */
    fun claim(): Claim? = FactKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) }
        ?.let { Claim(it, text, q, figures, tech, skills, mine) }
}
