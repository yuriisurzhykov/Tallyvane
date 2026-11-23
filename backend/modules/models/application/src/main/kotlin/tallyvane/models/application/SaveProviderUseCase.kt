package tallyvane.models.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.UseCase

/**
 * An administrator adds a model provider or changes one: address, key, models and prices.
 */
public interface SaveProviderUseCase : UseCase {
    public suspend fun save(administrator: AccountId, form: ProviderForm): ModelsOutcome

    public class SaveProvider(private val administration: Administration) : SaveProviderUseCase {
        override suspend fun save(administrator: AccountId, form: ProviderForm): ModelsOutcome =
            administration.run(administrator) { book ->
                val fault = form.fault(isNew = book.list().none { it.id == form.id })
                when {
                    fault != null -> ModelsOutcome.Failed.Invalid(fault)
                    !book.save(form) -> ModelsOutcome.Failed.NameTaken()
                    else -> null
                }
            }

        override fun toString(): String = "SaveProvider"
    }
}
