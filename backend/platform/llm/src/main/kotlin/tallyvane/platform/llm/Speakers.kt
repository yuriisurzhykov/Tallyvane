package tallyvane.platform.llm

import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

/**
 * The client that speaks to a provider.
 */
internal interface Speakers {
    fun of(id: Uuid, kind: ProviderKind, baseUrl: String?, key: Secret): Speaker
}
