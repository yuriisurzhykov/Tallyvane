package tallyvane.platform.observability

import kotlin.time.Duration

/**
 * The aggregate and the per-dependency breakdown behind it.
 *
 * There is no `live` field: liveness asks nothing of any dependency, so holding a
 * report already answers it.
 */
public data class HealthReport(val status: Health, val ready: Boolean, val checks: List<Checked>) {
    public data class Checked(val name: String, val health: Health, val took: Duration)
}
