package tallyvane.models.application

/**
 * What one provider, model and spec cost over a period, in US dollars.
 */
@Suppress("LongParameterList") // One line of the cost table.
public class Cost(
    public val provider: String,
    public val model: String,
    public val spec: String,
    public val calls: Long,
    public val remembered: Long,
    public val tokensIn: Long,
    public val tokensOut: Long,
    public val dollars: Double,
) {
    override fun toString(): String = "Cost($provider, $model, $spec)"
}
