package tallyvane.models.infrastructure

import tallyvane.models.application.ProviderBookConformance
import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.llm.KeyCipher
import tallyvane.platform.llm.ProviderRecords
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

private const val KEYSET =
    """{"primaryKeyId":3531006433,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey",""" +
        """"value":"GiDjSQOCM3EjyHQNFk/u0zsYkbXnGSOlnyXSEt+C3zSQ8w==","keyMaterialType":"SYMMETRIC"},""" +
        """"status":"ENABLED","keyId":3531006433,"outputPrefixType":"TINK"}]}"""

class GatewayProviderBookIntegrationSpec : ProviderBookConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        return object : Subject {
            override val book: ProviderBook =
                ModelsStorageFactory().book(ProviderRecords(KeyCipher(Secret(KEYSET)), Clock.Wall()))
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
