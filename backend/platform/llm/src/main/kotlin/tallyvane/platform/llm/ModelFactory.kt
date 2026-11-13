package tallyvane.platform.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner

/**
 * Hands out the model every module asks, built once by the composition root.
 */
public class ModelFactory {
    /**
     * The model, behind its memo and its budget: memo first, then the budget, then the provider. Without a
     * [key] the installation runs with [Model.Absent], and the memo still answers what it already knows.
     */
    public fun model(
        key: Secret?,
        names: ModelNames,
        tokensADay: Long,
        transactions: TransactionRunner,
        clock: Clock,
    ): Model {
        val provider = if (key == null) {
            Model.Absent()
        } else {
            AnthropicModel(AnthropicOkHttpClient.builder().apiKey(key.revealed()).build(), names)
        }
        return RememberingModel(
            BudgetedModel(provider, PostgresSpending(transactions), clock, tokensADay),
            PostgresMemo(transactions),
            names,
        )
    }

    override fun toString(): String = "ModelFactory"
}
