package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.UseCase
import kotlin.uuid.Uuid

/**
 * An administrator chooses the provider every model call goes to from now on.
 */
public interface ChooseProviderUseCase : UseCase {
    public suspend fun choose(administrator: AccountId, provider: Uuid): ModelsOutcome

    public class ChooseProvider(private val administration: Administration) : ChooseProviderUseCase {
        override suspend fun choose(administrator: AccountId, provider: Uuid): ModelsOutcome =
            administration.run(administrator) { book ->
                if (book.activate(provider)) null else ModelsOutcome.Failed.Missing()
            }

        override fun toString(): String = "ChooseProvider"
    }
}
