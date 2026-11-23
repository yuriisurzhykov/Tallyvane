package tallyvane.models.web

import kotlinx.serialization.Serializable

@Serializable
internal class ModelsShown(val providers: List<ProviderShown>, val costs: List<CostShown>)
