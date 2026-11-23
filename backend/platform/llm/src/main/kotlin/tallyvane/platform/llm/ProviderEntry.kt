package tallyvane.platform.llm

import kotlin.uuid.Uuid

/**
 * A provider as the admin screen shows it: everything but the key, of which only the last characters are told.
 */
@Suppress("LongParameterList") // What the screen shows of a provider.
public class ProviderEntry(
    public val id: Uuid,
    public val name: String,
    public val kind: ProviderKind,
    public val baseUrl: String?,
    public val keyHint: String,
    public val fast: String,
    public val careful: String,
    public val priceIn: Double,
    public val priceOut: Double,
    public val active: Boolean,
) {
    override fun toString(): String = "ProviderEntry($name, $kind)"
}
