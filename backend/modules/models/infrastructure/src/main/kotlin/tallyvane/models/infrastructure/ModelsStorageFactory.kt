package tallyvane.models.infrastructure

import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.llm.ProviderRecords

public class ModelsStorageFactory {
    public fun book(records: ProviderRecords): ProviderBook = GatewayProviderBook(records)

    override fun toString(): String = "ModelsStorageFactory"
}
