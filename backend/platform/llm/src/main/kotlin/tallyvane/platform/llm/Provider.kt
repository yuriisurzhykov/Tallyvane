package tallyvane.platform.llm

import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.core.Speaker
import tallyvane.platform.llm.core.Speakers
import kotlin.uuid.Uuid

/**
 * One model provider as an administrator set it up: where to reach it, with which key, which model serves each
 * tier, and what a million tokens cost there, in US dollars.
 */
@Suppress("LongParameterList") // What a provider is.
public class Provider(
    private val id: Uuid,
    private val kind: ProviderKind,
    private val baseUrl: String?,
    private val key: Secret,
    private val fast: String,
    private val careful: String,
    private val pricePerMillionIn: Double,
    private val pricePerMillionOut: Double,
) {
    public fun modelFor(tier: ModelTier): String = if (tier == ModelTier.Fast) fast else careful

    /**
     * What [tokens] cost here, in US dollars.
     */
    public fun costOf(tokens: TokenCount): Double =
        (tokens.input * pricePerMillionIn + tokens.output * pricePerMillionOut) / MILLION

    /**
     * Where answers of this provider and [model] are remembered: the same question to another model is a new one.
     */
    public fun memoPart(model: String): String = "$kind|${baseUrl.orEmpty()}|$model"

    internal fun speakWith(speakers: Speakers): Speaker = speakers.of(id, kind, baseUrl, key)

    public fun id(): Uuid = id

    override fun equals(other: Any?): Boolean = other is Provider && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Provider($kind, $fast, $careful)"

    private companion object {
        const val MILLION = 1_000_000.0
    }
}
