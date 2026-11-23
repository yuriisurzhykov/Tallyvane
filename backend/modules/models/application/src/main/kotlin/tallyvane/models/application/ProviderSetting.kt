package tallyvane.models.application

import kotlin.uuid.Uuid

/**
 * A model provider as the admin screen shows it. The key is never shown, only its last characters.
 * [kind] is `anthropic` or `openai_compatible`; prices are US dollars per million tokens.
 */
@Suppress("LongParameterList") // What the screen shows.
public class ProviderSetting(
    public val id: Uuid,
    public val name: String,
    public val kind: String,
    public val baseUrl: String?,
    public val keyHint: String,
    public val fast: String,
    public val careful: String,
    public val priceIn: Double,
    public val priceOut: Double,
    public val active: Boolean,
) {
    override fun toString(): String = "ProviderSetting($name, $kind)"
}
