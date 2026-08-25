package tallyvane.platform.observability.health

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * A [HealthCheck] that answers as told, or takes [takes] to do it, or throws.
 *
 * Suspends rather than blocks, which is the friendly case; a check that blocks
 * its thread behaves differently enough to need its own double, in
 * `HealthCheckBoundedSpec`.
 */
internal class HealthCheckFake(
    override val name: String,
    override val requiredForReadiness: Boolean = true,
    private val takes: Duration = Duration.ZERO,
    private val answer: Health = Health.Up,
    private val throws: Throwable? = null,
) : HealthCheck {
    override suspend fun check(): Health {
        delay(takes)
        throws?.let { throw it }
        return answer
    }
}
