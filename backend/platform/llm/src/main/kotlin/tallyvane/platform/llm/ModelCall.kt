package tallyvane.platform.llm

import kotlin.uuid.Uuid

/**
 * One question to the model, as a spec defines it (`docs/specs/model`).
 *
 * @property spec The name and version of the spec, such as `extract_facts@1`. It is part of the memo's key, so
 * changing the instructions or the reply's format means a new version.
 * @property instructions What does not change between calls of this spec; sent first and marked for caching.
 * @property input The JSON this call is about.
 * @property onBehalfOf The account whose budget pays for the call.
 */
public class ModelCall(
    public val spec: String,
    public val tier: ModelTier,
    public val instructions: String,
    public val input: String,
    public val onBehalfOf: Uuid,
) {
    init {
        require(SPEC.matches(spec)) { "A spec is named like extract_facts@1, but was given $spec." }
    }

    override fun toString(): String = "ModelCall($spec)"

    private companion object {
        val SPEC = Regex("""[a-z][a-z_]*@[1-9][0-9]*""")
    }
}
