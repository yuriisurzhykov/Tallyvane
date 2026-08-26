package tallyvane.playground.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthCheck
import tallyvane.platform.observability.health.HealthReport
import tallyvane.platform.observability.health.HealthReporter
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.DatabaseAnswers
import tallyvane.platform.persistence.FlywayMigrations
import tallyvane.platform.persistence.MigrationsApplied
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val access =
    DatabaseAccess(
        url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5441/demo"),
        user = System.getProperty("spike.user", "demo"),
        password = System.getProperty("spike.password", "demo"),
    )

private val bound = 2.seconds

/** A dependency that answers, slowly or not at all, without needing the real thing. */
private class Stub(
    override val name: String,
    override val requiredForReadiness: Boolean,
    private val takes: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    private val answer: () -> Health,
) : HealthCheck {
    override suspend fun check(): Health {
        delay(takes)
        return answer()
    }
}

private fun line(checked: HealthReport.Checked): String =
    "    %-9s %-9s %5s ms   %s".format(
        checked.name,
        word(checked.health),
        checked.took.inWholeMilliseconds,
        cause(checked.health),
    )

private fun word(health: Health): String = when (health) {
    is Health.Up -> "up"
    is Health.Degraded -> "degraded"
    is Health.Down -> "down"
}

private fun cause(health: Health): String = when (health) {
    is Health.Up -> ""
    is Health.Degraded -> render(health.cause)
    is Health.Down -> render(health.cause)
}

private fun render(ailment: Ailment): String = when (ailment) {
    is Ailment.Refused -> """{ kind: refused, says: "${ailment.says}" }"""
    is Ailment.Overran -> "{ kind: overran, bound_ms: ${ailment.bound.inWholeMilliseconds} }"
    is Ailment.Threw -> "{ kind: threw, type: ${ailment.type} }"
    is Ailment.Dependencies -> "{ kind: dependencies, names: ${ailment.names} }"
    is Ailment.Behind -> "{ kind: behind, versions: ${ailment.versions} }"
}

private fun report(title: String, report: HealthReport, took: kotlin.time.Duration) {
    println()
    println("=== $title")
    println("  aggregate: ${word(report.status)}  ${cause(report.status)}")
    println("  ready:     ${report.ready}")
    println("  report took ${took.inWholeMilliseconds} ms, checks summed to ${report.checks.sumOf { it.took.inWholeMilliseconds }} ms")
    println("  checks:")
    report.checks.forEach { checked -> println(line(checked)) }
}

/**
 * What §11.3 hands an unauthenticated caller versus an authorised one (ADR-055).
 *
 * Written by hand here. The real rendering belongs to slice 12 and this is not it — the point
 * is only to show which fields cross which boundary.
 */
private fun audiences(report: HealthReport) {
    println("  what an unauthenticated caller sees:")
    println("""    { "status": "${word(report.status)}" }""")
    println("  what an authorised reader sees:")
    println("""    { "status": "${word(report.status)}", "ready": ${report.ready}, "checks": [""")
    report.checks.forEach { checked ->
        val causeText = cause(checked.health).takeIf { it.isNotEmpty() }?.let { """, "cause": $it""" } ?: ""
        println(
            """        { "name": "${checked.name}", "status": "${word(checked.health)}", """ +
                """"took_ms": ${checked.took.inWholeMilliseconds}$causeText },""",
        )
    }
    println("    ] }")
}

fun main(): Unit =
    runBlocking {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        println("Connecting to ${access.url} as ${access.user}")

        // `app` owns this: work abandoned by a timeout has to live somewhere that outlives one
        // call, and something has to cancel it on shutdown.
        val abandoned = CoroutineScope(SupervisorJob())

        PostgresPersistence(access).use { persistence ->
            val migrations = FlywayMigrations(access)

            // Exactly what a composition root does: take what each platform module offers,
            // wrap every one in the same two decorators, hand the list to the aggregator.
            fun guarded(check: HealthCheck): HealthCheck =
                HealthCheck.Contained(HealthCheck.Bounded(check, bound, abandoned))

            val real =
                listOf(
                    guarded(DatabaseAnswers(persistence.transactions)),
                    guarded(MigrationsApplied(migrations)),
                )

            timed("only the two real checks, on a database nobody migrated") {
                HealthReporter.OverChecks(real).report()
            }

            println()
            println("--- applying migrations, the way the deploy would")
            println("    applied ${migrations.apply().count} migration(s)")

            timed("the same two checks, after the deploy applied them") {
                HealthReporter.OverChecks(real).report()
            }

            val everything =
                real +
                    listOf(
                        // Not required for readiness: a degraded model does not stop the
                        // application serving everything that does not need one.
                        guarded(Stub("llm", requiredForReadiness = false, takes = 10.seconds) { Health.Up }),
                        guarded(Stub("storage", requiredForReadiness = true) { error("bucket unreachable") }),
                        guarded(
                            Stub("outbox", requiredForReadiness = false) {
                                Health.Degraded(Ailment.Refused("queue depth 1420, oldest 9m"))
                            },
                        ),
                    )

            val full = timed("five checks: two real, three stubbed to fail in different ways") {
                HealthReporter.OverChecks(everything).report()
            }

            println()
            println("=== the same report, as the two endpoints would render it")
            audiences(full)
        }

        abandoned.cancel()
        println()
        println("Abandoned scope cancelled - the 10s stub is still parked on a thread until it")
        println("finishes; cancelling only stops anyone waiting for it. That is why Bounded's")
        println("KDoc says the driver's own timeouts are what actually free a blocked thread.")
    }

private suspend fun timed(title: String, produce: suspend () -> HealthReport): HealthReport {
    val started = TimeSource.Monotonic.markNow()
    val produced = produce()
    report(title, produced, started.elapsedNow())
    return produced
}
