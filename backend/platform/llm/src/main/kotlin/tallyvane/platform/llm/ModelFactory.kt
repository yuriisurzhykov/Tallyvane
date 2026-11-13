package tallyvane.platform.llm

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner

/**
 * The model for the composition root: the provider an administrator chose, remembered and recorded, within a
 * daily budget per person. [bookkeeping] is a runner of its own, outside any request's transaction.
 */
public class ModelFactory {
    public fun model(records: ProviderRecords, tokensADay: Long, bookkeeping: TransactionRunner, clock: Clock): Model =
        BudgetedModel(
            RoutedModel(
                PostgresProviders(records, bookkeeping),
                ClientSpeakers(),
                PostgresMemo(bookkeeping),
                PostgresCallLog(bookkeeping),
                clock,
            ),
            PostgresSpending(bookkeeping),
            clock,
            tokensADay,
        )

    override fun toString(): String = "ModelFactory"
}
