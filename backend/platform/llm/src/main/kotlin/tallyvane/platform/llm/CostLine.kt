package tallyvane.platform.llm

/**
 * What one provider, model and spec cost over a period.
 */
@Suppress("LongParameterList") // One line of the cost table.
public class CostLine(
    public val provider: String,
    public val model: String,
    public val spec: String,
    public val calls: Long,
    public val remembered: Long,
    public val tokensIn: Long,
    public val tokensOut: Long,
    public val cost: Double,
) {
    override fun toString(): String = "CostLine($provider, $model, $spec)"
}
