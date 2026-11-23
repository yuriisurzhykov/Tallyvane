package tallyvane.models.web

import tallyvane.models.application.ChooseProviderUseCase
import tallyvane.models.application.RemoveProviderUseCase
import tallyvane.models.application.SaveProviderUseCase
import tallyvane.models.application.ShowModelsUseCase
import tallyvane.platform.http.RouteModule

public class ModelsRoutesFactory {
    public fun models(show: ShowModelsUseCase): RouteModule = ModelsRoutes(show, ModelsScreen(), ModelsProblems())

    public fun provider(save: SaveProviderUseCase): RouteModule = ProviderRoutes(save, ModelsScreen(), ModelsProblems())

    public fun removal(remove: RemoveProviderUseCase): RouteModule =
        ProviderRemovalRoutes(remove, ModelsScreen(), ModelsProblems())

    public fun active(choose: ChooseProviderUseCase): RouteModule =
        ActiveProviderRoutes(choose, ModelsScreen(), ModelsProblems())

    override fun toString(): String = "ModelsRoutesFactory"
}
