package tallyvane.platform.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import tallyvane.platform.observability.log.Trace
import tallyvane.platform.observability.log.TraceContext

/**
 * Everything `platform:http` installs on a Ktor application, in one call.
 *
 * `app` builds this with the modules it collected and the translators it composed; nothing else
 * touches the pipeline. That is deliberate: every guarantee below holds because there is exactly
 * one place where it is arranged, so no route can omit it and no future route can forget it.
 *
 * Six guarantees, in the order a request meets them:
 *
 * 1. **Every call runs under a trace.** Continued from `traceparent` if a valid one arrived,
 *    fresh otherwise, and carried in the coroutine context so every log line of that call has
 *    it (ADR-056).
 * 2. **Every response says which trace it was**, in the `traceparent` header — including the
 *    ones with no body, which is why the header exists as well as the body field.
 * 3. **A [Refused] renders correctly or not at all.** The table is asked here, with the
 *    [Answers] only this class holds; status comes from the document, the content type is
 *    `application/problem+json`, and the trace id is added to the body so it reaches the screen
 *    the user is looking at. A route hands over a failure and its table, and arranges none of
 *    this.
 * 4. **An escaped exception becomes an answer, never a leak.** The translator chain decides
 *    what; its tail produces a 500 with no detail at all. No stack trace, no exception message,
 *    no class name.
 * 5. **The framework's own failures follow the same contract.** A body that cannot be read is a
 *    400 rather than a 500, and an unmatched path answers in the error shape rather than with
 *    Ktor's bodiless 404 — both measured as wrong before they were arranged.
 * 6. **Only a failure that is ours is logged at ERROR.** A 4xx is the caller's business; logging
 *    it at a level that means "a human must look" (§16.6) would bury the 5xx that does.
 *
 * @param routes the modules to mount, each under `/api/v1` plus its own [RouteModule.basePath].
 * @param failures the modules' links only. This class puts the framework's own translator at
 * the head and the detail-free 500 at the tail, so neither end can be forgotten.
 * @param trace reads and writes `traceparent`.
 */
public class Api(
    private val routes: List<RouteModule>,
    private val failures: FailureTranslator,
    private val trace: TraceHeader,
) {
    init {
        val repeated = routes.groupBy { module -> module.basePath }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) {
            "Two modules mounted at the same path would shadow each other silently: $repeated"
        }
    }

    public fun install(application: Application) {
        application.install(ContentNegotiation) { json(ApiJson.format) }
        application.install(StatusPages) {
            exception<Throwable> { call, cause -> call.respondProblem(call.translated(cause)) }
            // Ktor answers an unmatched path with a bare 404 and no body at all — measured, and it
            // meant the one error format held everywhere except the most common failure there is.
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respondProblem(with(answers) { missing() })
            }
        }
        traced(application)
        renderRefusals(application)
        mount(application)
    }

    /**
     * Asks the chain, and logs only what is ours to fix — under the call's own trace.
     *
     * A 4xx is the client's business and says nothing about this system's health; logging it at
     * ERROR would bury the 5xx that does. §16.6 is explicit that a level means "a human must look",
     * and a stranger's typo does not qualify.
     *
     * The trace is restored explicitly, because an exception unwinds past the `withContext` that
     * carried it: measured live, the ERROR line for a 500 arrived with `"mdc": {}` and its body with
     * no `trace_id` — so the one case where a user quotes an id was the one case where the id was
     * nowhere to be found. The call's attributes survive the unwinding; a coroutine context element
     * does not.
     */
    private suspend fun ApplicationCall.translated(cause: Throwable): Problem {
        val problem = with(answers) { with(chain) { translate(cause) } ?: unexpected() }
        if (problem.status >= SERVER_FAULT) {
            withContext(TraceContext(traced())) { logger.error("Request failed", cause) }
        }
        return problem
    }

    /**
     * Wraps the whole call in a [TraceContext] element, which is what carries the ids across the
     * suspension points inside a handler. A bare MDC put would not survive them — measured in
     * ADR-056.
     */
    private fun traced(application: Application) {
        application.intercept(ApplicationCallPipeline.Setup) {
            val current = trace.read(call.request.headers[HEADER])
            // Both: the context element carries it through the handler's suspensions, the attribute
            // survives an exception unwinding past that element.
            call.attributes.put(TRACE, current)
            call.response.headers.append(HEADER, trace.write(current))
            withContext(TraceContext(current)) { proceed() }
        }
    }

    /**
     * The call's trace, from the attribute the interceptor set. Present for every call the
     * interceptor saw, which is all of them.
     */
    private fun ApplicationCall.traced(): Trace = attributes[TRACE]

    /**
     * The single place a [Problem] becomes bytes. Intercepting the send pipeline rather than
     * offering a helper function is what makes it unavoidable: a route cannot respond with a
     * problem and skip this.
     */
    /**
     * Recognises a [Refused] and asks its table, with the [Answers] nobody else holds.
     *
     * `Before`, not `Transform`: ContentNegotiation also intercepts the send pipeline, and at
     * `Transform` it had already turned the body into plain `application/json` with a 200 —
     * measured, three tests red. Rendering first and handing on an `OutgoingContent` leaves
     * ContentNegotiation nothing to convert.
     */
    private fun renderRefusals(application: Application) {
        application.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            val refused = subject as? Refused<*> ?: return@intercept
            proceedWith(rendered(refused.problem(answers), call.traced().traceId.value))
        }
    }

    private suspend fun ApplicationCall.respondProblem(problem: Problem) {
        respond(rendered(problem, traced().traceId.value))
    }

    private fun rendered(problem: Problem, traceId: String): TextContent {
        val fields = ApiJson.format.encodeToJsonElement(Problem.serializer(), problem).jsonObject.toMutableMap()
        fields["trace_id"] = JsonPrimitive(traceId)
        return TextContent(
            text = ApiJson.format.encodeToString(JsonObject(fields)),
            contentType = PROBLEM_JSON,
            status = HttpStatusCode.fromValue(problem.status),
        )
    }

    private fun mount(application: Application) {
        application.routing {
            route(VERSIONED) {
                routes.forEach { module ->
                    route(module.basePath.value) { module.install(this) }
                }
            }
        }
    }

    /**
     * The one instance of the only source of a [Problem]. Held here, never handed out: modules
     * receive it as a receiver inside [Problems.of] and [FailureTranslator.translate], which is
     * what makes those two the only places an error answer can be made.
     */
    private val answers: Answers = Rfc9457Answers()

    /**
     * The chain as it is actually asked: the framework's own failures first, the modules' next, the
     * detail-free 500 last.
     *
     * Both ends are added here rather than left to `app`. A transport failure can happen before any
     * module's code runs, and a chain whose tail someone forgot would let an unrecognised failure
     * reach Ktor's default handler — which is exactly the leak this class exists to prevent.
     */
    private val chain: FailureTranslator =
        FailureTranslator.Chained(listOf(TransportFailures(), failures, FailureTranslator.Unrecognised()))

    private companion object {
        const val HEADER = "traceparent"

        val TRACE = AttributeKey<Trace>("tallyvane.trace")

        /**
         * Below this a failure is the caller's to fix; at or above it, ours.
         */
        const val SERVER_FAULT = 500

        /**
         * §11.1: the version lives in the path, and a module never writes it itself.
         */
        const val VERSIONED = "/api/v1"

        val PROBLEM_JSON = ContentType("application", "problem+json")

        val logger = LoggerFactory.getLogger(Api::class.java)
    }
}
