package tallyvane.playground.http

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import tallyvane.platform.http.status.Answers
import tallyvane.platform.http.Api
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.IdGenerator

@Serializable
private data class Payslip(val takeHomeCents: Int, val paidOn: String)

/** What a module's failures look like: one sealed branch, so one mapping table covers it. */
private sealed interface Refusal : Failure {
    data object Range : Refusal

    data object Owner : Refusal
}

private class Refusals : Problems<Refusal> {
    override fun Answers.of(failure: Refusal): Problem = when (failure) {
        is Refusal.Range -> invalid(listOf(FieldError("salary_min_cents", "range.invalid")), "Minimum exceeds maximum")
        is Refusal.Owner -> forbidden("Not your payslip")
    }
}

private class Probes(private val problems: Refusals) : RouteModule {
    override val basePath: BasePath = BasePath("/probes")

    private val logger = LoggerFactory.getLogger(Probes::class.java)

    override fun install(route: Route) {
        route.get("/ok") {
            // Watch the trace_id on this line and in the response header: they are the same.
            logger.info("Answering the happy path")
            call.respond(Payslip(takeHomeCents = 512_00, paidOn = "2026-08-31"))
        }
        route.get("/refused") { call.respond(Refused(Refusal.Range, problems)) }
        route.get("/forbidden") { call.respond(Refused(Refusal.Owner, problems)) }
        route.get("/boom") {
            error("jdbc:postgresql://tallyvane:hunter2@10.0.0.4:5432/db is unreachable")
        }
        route.post("/echo") { call.respond(call.receive<Payslip>()) }
    }
}

// 8099 rather than 8080: 8080 was already taken on the first machine this ran on, and the
// failure — a BindException after the application had already logged "started" — is confusing
// enough that a quieter default is worth more than a familiar one. Override with -Pspike.port.
private val port = System.getProperty("spike.port", "8099").toInt()

/**
 * `curl.exe` on Windows, `curl` elsewhere.
 *
 * Not pedantry: in PowerShell `curl` is an alias for `Invoke-WebRequest`, which does not understand
 * `-i` and so starts prompting for a `Uri` instead of doing anything. The first version of this menu
 * printed `curl`, which is unusable in the shell this repository is developed in.
 */
private val windows = System.getProperty("os.name").startsWith("Windows")

private val client = if (windows) "curl.exe" else "curl"

/**
 * A JSON body spelled the way the host shell will actually deliver it.
 *
 * PowerShell rewrites quotes when it hands arguments to a native program, and it does so inside
 * single quotes too: `-d '{"a":1}'` arrived at the server mangled, and the endpoint answered 400 —
 * the same answer as a genuinely broken body, which is how this went unnoticed for a whole round of
 * "snake_case must not be read". Backslash-escaping the inner quotes survives; so does a body read
 * from a file, and both were checked against the running server rather than reasoned about.
 */
private fun body(json: String): String = if (windows) "'${json.replace("\"", "\\\"")}'" else "'$json'"

private const val GOOD_BODY = """{"take_home_cents":1,"paid_on":"2026-08-31"}"""

private fun menu() {
    val json = "-H 'content-type: application/json'"
    println()
    // ASCII only in everything printed: this runs in a Windows console that is not UTF-8, and an
    // em dash arrived as mojibake in the middle of the instructions.
    println("Listening on http://localhost:$port  - try these, and watch the log lines:")
    println()
    println("  $client -i localhost:$port/api/v1/probes/ok")
    println("      200, snake_case body, traceparent header; the log line carries the same trace_id")
    println("  $client -i localhost:$port/api/v1/probes/refused")
    println("      422 application/problem+json, errors[] with the field, trace_id in the body")
    println("  $client -i localhost:$port/api/v1/probes/forbidden")
    println("      403, same shape, no errors[]")
    println("  $client -i localhost:$port/api/v1/probes/boom")
    println("      500 with NO detail: the exception's message holds a password, and none of it leaves.")
    println("      The ERROR log line carries the same trace_id as the response - that pairing was")
    println("      broken until a live run found it.")
    println("  $client -i localhost:$port/api/v1/nowhere")
    println("      404 in the same shape, although Ktor's own 404 has no body at all. type is")
    println("      about:blank - no meaning beyond the code - while a module's own 404 keeps its")
    println("      own type and its detail. Both were identical until a review found it.")
    println("  $client -i -X POST localhost:$port/api/v1/probes/ok")
    println("      405 with a body, and nothing in platform:http mentions 405: the shape is given")
    println("      to whatever Ktor answers on its own, not to a list of codes")
    println("  $client -i -X POST localhost:$port/api/v1/probes/echo $json -d '{ oops'")
    println("      400, not 500: unreadable input is the caller's fault and is not logged as ours")
    println("  $client -i -X POST localhost:$port/api/v1/probes/echo $json -d ${body(GOOD_BODY)}")
    println("      200 - snake_case is read as well as written")
    println("  $client -i localhost:$port/api/v1/probes/ok -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'")
    println("      the response continues that trace instead of starting one")
    println()
    println("Every request also produces an 'access ...' line from Ktor's CallLogging. Compare the")
    println("mdc field on the /ok line with the one on the /boom line: the 500 has lost trace_id.")
    println()
    println("Ctrl+C to stop.")
}

/**
 * Ktor's own access log, kept here as the measurement it was installed for: does its line carry the
 * trace ids our `TraceContext` puts in MDC?
 *
 * The doubt was not idle. `StatusPages` turned out to run *outside* the context element, because an
 * exception unwinds past the `withContext` that carries it — found the same evening, in this spike.
 * `CallLogging` logs after the response is sent, which is the same neighbourhood.
 *
 * Answer, from the run recorded in this spike's README: the ambient ids reach the line for a 200 and
 * for Ktor's own 404, and are **gone** for a 500 — `"mdc": {}` on exactly the line most worth
 * correlating. `mdc("from_call")` is the other half of the measurement and survives all three,
 * because that hook reads from the call rather than from the thread. So an access log can be
 * correlated, but only by being told where the trace lives; it does not come for free.
 */
private fun callLogging(application: Application) {
    application.install(CallLogging) {
        level = Level.INFO
        format { call -> "access ${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value}" }
        mdc("from_call") { call -> call.request.path() }
    }
}

/**
 * Whether the port is free, asked by binding a plain socket and letting go of it.
 *
 * A spike that fails has to fail legibly. Left to Ktor, a busy port produces a
 * `JobCancellationException` with forty lines of coroutine internals and the actual cause —
 * `BindException` — at the bottom, printed *after* a cheerful "Listening on …" banner that had
 * already gone out. Reported by the author on the second launch of a spike that was still running
 * from the first, which is the way this fails almost every time.
 */
private fun free(): Boolean = try {
    java.net.ServerSocket(port).close()
    true
} catch (busy: java.io.IOException) {
    println("Port $port is already in use (${busy.message}).")
    println("Most likely this spike is still running from an earlier launch - stop it, or use")
    println("another port:  ./gradlew :playground:http:run -Pspike.port=9000")
    false
}

fun main() {
    if (!free()) {
        return
    }
    val api =
        Api(
            routes = listOf(Probes(Refusals())),
            // Empty on purpose: `Api` adds the framework's translator at the head and the
            // detail-free 500 at the tail itself, and /boom below proves the tail works.
            failures = FailureTranslator.Chained(emptyList()),
            trace = TraceHeader(IdGenerator.Uuid7()),
        )
    menu()
    // The check above closes the common case; this closes the race between it and here, so a
    // failure to bind never reaches the console as a coroutine stack trace.
    try {
        embeddedServer(CIO, port = port) {
            api.install(this)
            callLogging(this)
        }.start(wait = true)
    } catch (refused: Throwable) {
        val cause = generateSequence(refused) { it.cause }.firstOrNull { it is java.net.BindException }
        if (cause == null) throw refused
        println("Could not take port $port after all: ${cause.message}. Try -Pspike.port=9000.")
    }
}
