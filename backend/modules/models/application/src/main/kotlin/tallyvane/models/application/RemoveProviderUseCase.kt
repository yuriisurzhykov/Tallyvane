package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.UseCase
import kotlin.uuid.Uuid

/**
 * An administrator removes a provider and its key. What its calls cost stays in the log.
 */
public interface RemoveProviderUseCase : UseCase {
    public suspend fun remove(administrator: AccountId, provider: Uuid): ModelsOutcome

    public class RemoveProvider(private val administration: Administration) : RemoveProviderUseCase {
        override suspend fun remove(administrator: AccountId, provider: Uuid): ModelsOutcome =
            administration.run(administrator) { book ->
                if (book.remove(provider)) null else ModelsOutcome.Failed.Missing()
            }

        override fun toString(): String = "RemoveProvider"
    }
}
