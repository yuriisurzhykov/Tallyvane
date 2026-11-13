package tallyvane.platform.llm

/**
 * Which model each tier is, by the provider's identifier.
 */
public class ModelNames(private val fast: String, private val careful: String) {
    init {
        require(fast.isNotBlank() && careful.isNotBlank()) { "A tier names a model." }
    }

    public fun of(tier: ModelTier): String = when (tier) {
        ModelTier.Fast -> fast
        ModelTier.Careful -> careful
    }

    override fun toString(): String = "ModelNames(fast=$fast, careful=$careful)"
}
