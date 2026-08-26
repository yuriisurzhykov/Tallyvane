package tallyvane.platform.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import tallyvane.platform.observability.log.TraceContext

/**
 * Everything `platform:http` installs on a Ktor application, in one call.
 *
 * `app` builds this with the modules it collected and the translators it composed; nothing else
 * touches the pipeline. That is deliberate: every guarantee below holds because there is exactly
 * one place where it is arranged, so no route can omit it and no future route can forget it.
 *
 * Four guarantees, in the order a request meets them:
 *
 * 1. **Every call runs under a trace.** Continued from `traceparent` if a valid one arrived,
 *    fresh otherwise, and carried in the coroutine context so every log line of that call has
 *    it (ADR-056).
 * 2. **Every response says which trace it was**, in the `traceparent` header — including the
 *    ones with no body, which is why the header exists as well as the body field.
 * 3. **A [Problem] renders itself correctly or not at all.** Status from the document, content
 *    type `application/problem+json`, the trace id added to the body so it reaches the screen
 *    the user is looking at. A route responds with the value and arranges none of this.
 * 4. **An escaped exception becomes an answer, never a leak.** The translator chain decides
 *    what; its tail produces a 500 with no detail at all. No stack trace, no exception message,
 *    no class name.
 *
 * @param routes the modules to mount, each under `/api/v1` plus its own [RouteModule.basePath].
 * @param failures the chain for exceptions no use case reported; must end in
 * [FailureTranslator.Unrecognised], or an unrecognised failure would reach Ktor's own handler.
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
            exception<Throwable> { call, cause ->
                logger.error("Request failed", cause)
                call.respond(failures.translate(cause) ?: Problem.unexpected())
            }
        }
        traced(application)
        renderProblems(application)
        mount(application)
    }

    /**
     * Wraps the whole call in a [TraceContext] element, which is what carries the ids across the
     * suspension points inside a handler. A bare MDC put would not survive them — measured in
     * ADR-056.
     */
    private fun traced(application: Application) {
        application.intercept(ApplicationCallPipeline.Setup) {
            val current = trace.read(call.request.headers[HEADER])
            call.response.headers.append(HEADER, trace.write(current))
            withContext(TraceContext(current)) { proceed() }
        }
    }

    /**
     * The single place a [Problem] becomes bytes. Intercepting the send pipeline rather than
     * offering a helper function is what makes it unavoidable: a route cannot respond with a
     * problem and skip this.
     */
    private fun renderProblems(application: Application) {
        // `Before`, not `Transform`: ContentNegotiation also intercepts the send pipeline, and at
        // `Transform` it had already turned the problem into plain `application/json` with a 200 —
        // measured, three tests red. Rendering first and handing on an `OutgoingContent` leaves
        // ContentNegotiation nothing to convert.
        application.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            val problem = subject as? Problem ?: return@intercept
            proceedWith(rendered(problem, TraceContext.current()?.traceId?.value))
        }
    }

    private fun rendered(problem: Problem, traceId: String?): TextContent {
        val fields = ApiJson.format.encodeToJsonElement(Problem.serializer(), problem).jsonObject.toMutableMap()
        traceId?.let { id -> fields["trace_id"] = JsonPrimitive(id) }
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

    private companion object {
        const val HEADER = "traceparent"

        /**
         * §11.1: the version lives in the path, and a module never writes it itself.
         */
        const val VERSIONED = "/api/v1"

        val PROBLEM_JSON = ContentType("application", "problem+json")

        val logger = LoggerFactory.getLogger(Api::class.java)
    }
}
