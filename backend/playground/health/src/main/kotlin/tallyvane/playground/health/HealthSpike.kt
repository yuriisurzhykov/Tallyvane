package tallyvane.playground.health

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tallyvane.platform.health.HealthRoutes
import tallyvane.platform.health.ServiceToken
import tallyvane.platform.http.Api
import tallyvane.platform.http.FailureTranslator
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
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
        password = Secret(System.getProperty("spike.password", "demo")),
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

/**
 * The report as a table, for the console phase only.
 *
 * Deliberately not the wire format. Until slice 12 this spike also *invented* the wire format by
 * printing JSON by hand, with a note admitting it was a guess at what the endpoints would do — which
 * is exactly the kind of second copy that drifts from the first. The endpoints exist now, so the JSON
 * comes from `platform:health` over a real socket and this rendering stays what it is good at:
 * showing five checks and their timings side by side.
 */
private fun report(title: String, report: HealthReport, took: kotlin.time.Duration) {
    println()
    println("=== $title")
    println("  aggregate: ${word(report.status)}  ${cause(report.status)}")
    println("  ready:     ${report.ready}")
    println("  report took ${took.inWholeMilliseconds} ms, checks summed to ${report.checks.sumOf { it.took.inWholeMilliseconds }} ms")
    println("  checks:")
    report.checks.forEach { checked -> println(line(checked)) }
}

// 8098, one below the http spike's 8099, so both can run at once - which is the normal way to
// compare their log output.
private val port = System.getProperty("spike.port", "8098").toInt()

/**
 * `curl.exe` on Windows: in PowerShell `curl` is an alias for `Invoke-WebRequest`, which does not
 * understand `-i` and starts prompting for a `Uri` instead of doing anything. The http spike printed
 * `curl` at first and it was unusable in the shell this repository is developed in.
 */
private val client = if (System.getProperty("os.name").startsWith("Windows")) "curl.exe" else "curl"

/**
 * A secret in source, which is only ever acceptable because this process serves a stub report on a
 * localhost port. The deploy supplies the real one; ADR-063 says where from.
 */
private const val TOKEN = "spike-service-token"

private fun menu() {
    println()
    // ASCII only: this runs in a Windows console that is not UTF-8, and an em dash arrived as
    // mojibake in the middle of the http spike's instructions.
    println("Serving the real endpoints on http://localhost:$port")
    println()
    println("  $client -i localhost:$port/api/v1/health")
    println("      200 with { \"status\": \"down\" } and nothing else - no check names, no messages.")
    println("      'storage' is stubbed to throw 'bucket unreachable'; none of that text is here.")
    println("  $client -i localhost:$port/api/v1/health -H 'x-service-token: $TOKEN'")
    println("      200 with the breakdown: five checks, took_ms each, cause objects tagged by kind.")
    println("      Add the took_ms up and compare with how long the request took - they run together.")
    println("  $client -i localhost:$port/api/v1/health -H 'x-service-token: wrong'")
    println("      200 and the summary again, NOT 401: a bad token is a caller who gets the public")
    println("      answer, so a broken monitor never looks like an outage.")
    println("  $client -i localhost:$port/api/v1/health/live")
    println("      200 in about 20 ms, all of it curl starting up, because no check runs. /ready on")
    println("      this same process takes about 2 s, and the two real checks alone about 115 ms -")
    println("      and an orchestrator asks every few seconds.")
    println("  $client -i localhost:$port/api/v1/health/ready")
    println("      503: 'storage' is down and is required for readiness. 'llm' overran its bound and")
    println("      'outbox' is degraded, and neither of those closes the door - that is the point of")
    println("      requiredForReadiness.")
    println()
    println("Every response carries Cache-Control: no-store - Cloudflare would otherwise be within")
    println("its rights to cache a cheerful 'up' while this is on the floor.")
    println()
    println("Ctrl+C to stop.")
}

/**
 * Whether the port is free, asked by binding a plain socket and letting go of it. Left to Ktor, a
 * busy port produces a `JobCancellationException` with the real cause forty lines down, printed after
 * a banner that already said "serving".
 */
private fun free(): Boolean = try {
    java.net.ServerSocket(port).close()
    true
} catch (busy: java.io.IOException) {
    println("Port $port is already in use (${busy.message}).")
    println("Stop the earlier launch, or use another port:")
    println("  ./gradlew :playground:health:run -Pspike.port=9000")
    false
}

fun main(): Unit =
    runBlocking {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        if (!free()) {
            return@runBlocking
        }
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

            timed("five checks: two real, three stubbed to fail in different ways") {
                HealthReporter.OverChecks(everything).report()
            }

            val api =
                Api(
                    routes = listOf(HealthRoutes(HealthReporter.OverChecks(everything), ServiceToken(TOKEN))),
                    // Empty: `Api` puts the framework's translator at the head and the detail-free
                    // 500 at the tail itself, and health has no failures of its own to map.
                    failures = FailureTranslator.Chained(emptyList()),
                    trace = TraceHeader(IdGenerator.Uuid7()),
                )
            menu()
            try {
                embeddedServer(CIO, port = port) { api.install(this) }.start(wait = true)
            } catch (refused: Throwable) {
                val cause = generateSequence(refused) { it.cause }.firstOrNull { it is java.net.BindException }
                if (cause == null) throw refused
                println("Could not take port $port after all: ${cause.message}. Try -Pspike.port=9000.")
            }
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
