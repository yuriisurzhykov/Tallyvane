package tallyvane.platform.observability

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeSource

/**
 * What a route needs in order to answer a probe: one report, however it is produced.
 */
public interface HealthReporter {
    public suspend fun report(): HealthReport

    /**
     * Asks every check at once, so the worst case is the longest check instead of
     * the sum of them. §6.11 composes a briefing the same way.
     *
     * Folds answers and nothing else: bounding a check is
     * [HealthCheck.Bounded]'s job and surviving one is [HealthCheck.Contained]'s,
     * both applied by `app` when it collects the checks. An earlier draft did all
     * three here, which gave this class a coroutine scope of its own, a lifetime,
     * and a question about what to answer once that lifetime ended — none of
     * which aggregation needs.
     */
    public class OverChecks(private val checks: List<HealthCheck>) : HealthReporter {
        init {
            val repeated = checks.groupBy { it.name }.filterValues { it.size > 1 }.keys
            require(repeated.isEmpty()) {
                "Two checks named the same cannot be told apart in an alert: $repeated"
            }
        }

        override suspend fun report(): HealthReport {
            val results = coroutineScope {
                checks.map { check ->
                    async { check to checked(check) }
                }.awaitAll()
            }
            val unready = results.any { (check, checked) ->
                check.requiredForReadiness && checked.health is Health.Down
            }
            val ailing = results.filterNot { (_, checked) -> checked.health is Health.Up }
            return HealthReport(
                status = status(unready, ailing.map { (check, _) -> check.name }),
                ready = !unready,
                checks = results.map { (_, checked) -> checked },
            )
        }

        private suspend fun checked(check: HealthCheck): HealthReport.Checked {
            val started = TimeSource.Monotonic.markNow()
            return HealthReport.Checked(check.name, check.check(), started.elapsedNow())
        }

        private fun status(unready: Boolean, ailing: List<String>): Health = when {
            unready -> Health.Down(Ailment.Dependencies(ailing))
            ailing.isNotEmpty() -> Health.Degraded(Ailment.Dependencies(ailing))
            else -> Health.Up
        }
    }
}
