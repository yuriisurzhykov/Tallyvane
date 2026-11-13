package tallyvane.server.config

import tallyvane.platform.kernel.Secret

/**
 * How the product reaches the model: the Anthropic key (none: every model call answers "unavailable", and the
 * person's words are still kept), the model of each tier, and how many tokens one person may spend a day.
 */
public class ModelConfiguration(
    public val key: Secret?,
    public val fast: String,
    public val careful: String,
    public val tokensADay: Int,
) {
    override fun toString(): String = "ModelConfiguration(key=${if (key == null) "absent" else "set"}, $fast, $careful)"
}
