package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.UseCase

/**
 * Shows an administrator the model providers, which one is in use, and what the last 30 days of calls cost.
 */
public interface ShowModelsUseCase : UseCase {
    public suspend fun show(administrator: AccountId): ModelsOutcome

    public class ShowModels(private val administration: Administration) : ShowModelsUseCase {
        override suspend fun show(administrator: AccountId): ModelsOutcome = administration.run(administrator) { null }

        override fun toString(): String = "ShowModels"
    }
}
