package tallyvane.platform.health

import tallyvane.platform.observability.health.HealthReport
import tallyvane.platform.observability.health.HealthReporter

/**
 * A reporter that answers with whatever it was handed, and counts being asked.
 *
 * The count is the point rather than decoration: "liveness touches nothing" is only assertable if
 * something watched whether it asked.
 */
internal class HealthReporterFake(private val report: HealthReport) : HealthReporter {
    var asked: Int = 0
        private set

    override suspend fun report(): HealthReport {
        asked++
        return report
    }
}
