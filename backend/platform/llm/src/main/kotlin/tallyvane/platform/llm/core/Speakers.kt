package tallyvane.platform.llm.core

import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.ProviderKind
import kotlin.uuid.Uuid

/**
 * The client that speaks to a provider.
 */
internal interface Speakers {
    fun of(id: Uuid, kind: ProviderKind, baseUrl: String?, key: Secret): Speaker
}
