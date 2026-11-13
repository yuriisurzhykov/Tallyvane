package tallyvane.platform.llm

import tallyvane.platform.kernel.Clock
import tallyvane.platform.llm.core.Model
import tallyvane.platform.llm.core.Spending

/**
 * A [tallyvane.platform.llm.core.Model] that refuses a call once the account has spent its day, and writes down what each call cost.
 *
 * Wrapped by [RememberingModel] and not the other way round: an answer from the memo costs nothing and is
 * given even to an account over its budget.
 */
public class BudgetedModel(
    private val inner: Model,
    private val spending: Spending,
    private val clock: Clock,
    private val tokensADay: Long,
) : Model {
    init {
        require(tokensADay > 0) { "A daily budget is a positive number of tokens." }
    }

    override suspend fun ask(call: ModelCall): ModelReply {
        val day = clock.now().toString().take(DAY_LENGTH)
        if (spending.spentOn(call.onBehalfOf, day) >= tokensADay) {
            return ModelReply.OverBudget()
        }
        val reply = inner.ask(call)
        if (reply is ModelReply.Answered) {
            spending.add(call.onBehalfOf, day, call.spec, reply.tokens)
        }
        return reply
    }

    override fun toString(): String = "BudgetedModel($inner, tokensADay=$tokensADay)"

    private companion object {
        /**
         * `2026-11-13`, the head of an instant written in UTC.
         */
        const val DAY_LENGTH = 10
    }
}
