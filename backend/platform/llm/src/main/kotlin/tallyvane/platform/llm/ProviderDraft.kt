package tallyvane.platform.llm

import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

/**
 * A provider an administrator is saving. [key] null keeps the key already kept.
 */
@Suppress("LongParameterList") // What an administrator fills in.
public class ProviderDraft(
    public val id: Uuid,
    public val name: String,
    public val kind: ProviderKind,
    public val baseUrl: String?,
    public val key: Secret?,
    public val fast: String,
    public val careful: String,
    public val priceIn: Double,
    public val priceOut: Double,
) {
    override fun toString(): String = "ProviderDraft($name, $kind)"
}
