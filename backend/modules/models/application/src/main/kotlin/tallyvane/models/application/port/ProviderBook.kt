package tallyvane.models.application.port

import tallyvane.models.application.Cost
import tallyvane.models.application.ProviderForm
import tallyvane.models.application.ProviderSetting
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The model providers and what calls to them cost. Every call runs inside a transaction the caller opened.
 */
public interface ProviderBook {
    public fun list(): List<ProviderSetting>

    /**
     * Keeps [form]; false when another provider already has its name.
     */
    public fun save(form: ProviderForm): Boolean

    /**
     * Makes [id] the one provider in use; false when there is no such provider.
     */
    public fun activate(id: Uuid): Boolean

    public fun remove(id: Uuid): Boolean

    /**
     * What each provider, model and spec cost since [since], the most expensive first.
     */
    public fun costs(since: Instant): List<Cost>
}
