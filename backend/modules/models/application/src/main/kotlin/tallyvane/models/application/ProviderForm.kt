package tallyvane.models.application

import kotlin.uuid.Uuid

/**
 * A provider an administrator is saving. [key] null or blank keeps the key already kept.
 */
@Suppress("LongParameterList") // What the form holds.
public class ProviderForm(
    public val id: Uuid,
    public val name: String,
    public val kind: String,
    public val baseUrl: String?,
    public val key: String?,
    public val fast: String,
    public val careful: String,
    public val priceIn: Double,
    public val priceOut: Double,
) {
    /**
     * What is wrong with the form, or null when it can be kept.
     */
    public fun fault(isNew: Boolean): String? = whereFault() ?: keyFault(isNew) ?: modelFault()

    private fun whereFault(): String? = when {
        name.isBlank() || name.length > NAME -> "name: 1 to $NAME characters"
        kind !in KINDS -> "kind: one of ${KINDS.joinToString()}"
        kind == OPENAI && baseUrl == null -> "baseUrl: required, like https://api.openai.com/v1"
        baseUrl != null && !URL.matches(baseUrl) -> "baseUrl: an http(s) address"
        else -> null
    }

    private fun keyFault(isNew: Boolean): String? = when {
        isNew && key.isNullOrBlank() -> "key: required for a new provider"
        (key?.length ?: 0) > KEY -> "key: at most $KEY characters"
        else -> null
    }

    private fun modelFault(): String? = when {
        listOf(fast, careful).any {
            it.isBlank() || it.length > MODEL
        } -> "fast, careful: model names, 1 to $MODEL characters"
        listOf(priceIn, priceOut).any {
            it !in 0.0..PRICE
        } -> "priceIn, priceOut: 0 to $PRICE dollars per million tokens"
        else -> null
    }

    override fun toString(): String = "ProviderForm($name, $kind)"

    private companion object {
        const val NAME = 60
        const val KEY = 400
        const val MODEL = 120
        const val PRICE = 1_000.0
        const val OPENAI = "openai_compatible"
        val KINDS = listOf("anthropic", OPENAI)
        val URL = Regex("""https?://\S+""")
    }
}
