package tallyvane.platform.health

import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthReport

/**
 * Turns one report into the two shapes ADR-055 allows.
 *
 * A class rather than functions on the DTOs' companions, because the mapping branches and
 * `no-companion-logic` forbids a companion that does — rightly: a factory that decides is a factory
 * whose decisions nobody reviews.
 *
 * Both shapes come from here, so the difference between them is one diff away from a reader rather
 * than spread across three route handlers.
 */
internal class Presented {
    fun summary(report: HealthReport): Summary = Summary(status = word(report.status))

    fun detail(report: HealthReport): Detail = Detail(
        status = word(report.status),
        ready = report.ready,
        checks = report.checks.map { checked -> checked(checked) },
    )

    private fun checked(checked: HealthReport.Checked): Detail.Checked = Detail.Checked(
        name = checked.name,
        status = word(checked.health),
        tookMs = checked.took.inWholeMilliseconds,
        cause = cause(checked.health),
    )

    private fun word(health: Health): String = when (health) {
        is Health.Up -> "up"
        is Health.Degraded -> "degraded"
        is Health.Down -> "down"
    }

    private fun cause(health: Health): Cause? = when (health) {
        is Health.Up -> null
        is Health.Degraded -> renderable(health.cause)
        is Health.Down -> renderable(health.cause)
    }

    /**
     * `Dependencies` and `Behind` map to nothing: they name the system's parts and its schema
     * versions, which ADR-055 withholds from every answer. `null` here means the field is absent,
     * and `explicitNulls = false` in `ApiJson` keeps it out of the JSON entirely.
     */
    private fun renderable(ailment: Ailment): Cause? = when (ailment) {
        is Ailment.Refused -> Cause.Refused(ailment.says)
        is Ailment.Overran -> Cause.Overran(ailment.bound.inWholeMilliseconds)
        is Ailment.Threw -> Cause.Threw(ailment.type)
        is Ailment.Dependencies -> null
        is Ailment.Behind -> null
    }
}
