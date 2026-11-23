package tallyvane.server

import tallyvane.identity.contract.Admins
import tallyvane.models.application.Administration
import tallyvane.models.application.ChooseProviderUseCase
import tallyvane.models.application.RemoveProviderUseCase
import tallyvane.models.application.SaveProviderUseCase
import tallyvane.models.application.ShowModelsUseCase
import tallyvane.models.infrastructure.ModelsStorageFactory
import tallyvane.models.web.ModelsRoutesFactory
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.KeyCipher
import tallyvane.platform.llm.ProviderRecords

/**
 * The model providers: their records, shared with the model gateway, and the admin screen over them.
 * [keyset] is the Tink keyset that seals provider keys at rest.
 */
public class ModelsWiring(private val platform: PlatformWiring, private val admins: Admins, keyset: Secret) {
    public val records: ProviderRecords = ProviderRecords(KeyCipher(keyset), Clock.Wall())

    private val administration by lazy {
        Administration(ModelsStorageFactory().book(records), admins, platform.persistence.transactions, Clock.Wall())
    }

    public val routes: List<RouteModule> by lazy {
        val factory = ModelsRoutesFactory()
        listOf(
            factory.models(ShowModelsUseCase.ShowModels(administration)),
            factory.provider(SaveProviderUseCase.SaveProvider(administration)),
            factory.removal(RemoveProviderUseCase.RemoveProvider(administration)),
            factory.active(ChooseProviderUseCase.ChooseProvider(administration)),
        )
    }

    override fun toString(): String = "ModelsWiring"
}
