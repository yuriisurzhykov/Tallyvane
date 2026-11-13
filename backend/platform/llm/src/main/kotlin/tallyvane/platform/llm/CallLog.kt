package tallyvane.platform.llm

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Every model call, with what it cost, for comparing providers and models.
 */
public interface CallLog {
    @Suppress("LongParameterList") // What one call was.
    public suspend fun record(
        at: Instant,
        account: Uuid,
        spec: String,
        provider: Uuid,
        model: String,
        tokens: TokenCount,
        cost: Double,
        remembered: Boolean,
    )
}
