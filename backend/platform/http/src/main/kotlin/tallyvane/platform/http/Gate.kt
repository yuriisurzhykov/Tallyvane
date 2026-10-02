package tallyvane.platform.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

/**
 * Who may come in, and from where, before anything else about a request is looked at (ADR-080, ADR-088).
 *
 * Two interceptors do it, both installed by `Api` itself and ahead of the idempotency claim, so no
 * route can opt out and a refused request leaves nothing behind:
 *
 * 1. **Forgery.** An unsafe request must come from the application's own origin, exactly: its `Origin`
 *    header names it, or, where the browser sent none, `Sec-Fetch-Site` says `same-origin`. A request
 *    with neither is refused. A body must be `application/json`, which an HTML form cannot send.
 * 2. **Identity.** [Callers] says who the request comes from, once, and the answer is kept on the call.
 *    A route that is not [Access.Public] then lets only a signed-in person through.
 *
 * ### Closed unless clearly open
 *
 * The decision is made on the path before routing, and routing might read the same path differently.
 * So a path is open only when it names a public module's address plainly: no `.` or `..` segment, no
 * empty segment, no percent-encoding and no backslash. Anything odd falls on the closed side, whatever
 * the router would have made of it.
 */
internal class Gate(
    routes: List<RouteModule>,
    private val callers: Callers,
    private val appOrigin: String,
) {
    private val problems = AccessProblems()

    private val openPrefixes = routes.filter { it.access == Access.Public }.map { "$VERSIONED${it.basePath.value}" }

    fun install(application: Application) {
        application.intercept(ApplicationCallPipeline.Call) { refuseForgery() }
        application.intercept(ApplicationCallPipeline.Call) { identify() }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.refuseForgery() {
        if (call.request.httpMethod in Repeats.UNSAFE) {
            when {
                !fromOurOrigin(call) -> {
                    call.respond(Refused(AccessFailure.ForeignOrigin, problems))
                    finish()
                }

                !carriesJsonOrNothing(call) -> {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                    finish()
                }
            }
        }
    }

    private fun fromOurOrigin(call: PipelineCall): Boolean {
        val origin = call.request.headers[HttpHeaders.Origin]
        return if (origin != null) {
            origin == appOrigin
        } else {
            call.request.headers[SEC_FETCH_SITE] == SAME_ORIGIN
        }
    }

    private fun carriesJsonOrNothing(call: PipelineCall): Boolean {
        val declared = call.request.headers[HttpHeaders.ContentType]
        return if (declared != null) {
            call.request.contentType().match(ContentType.Application.Json)
        } else {
            call.request.contentLength() in setOf(null, 0L) && call.request.headers[HttpHeaders.TransferEncoding] == null
        }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.identify() {
        val caller = callers.of(call)
        call.attributes.put(CALLER, caller)
        val refusal = if (closed(call.request.path())) caller.reportTo(Admitting()) else null
        if (refusal != null) {
            call.respond(Refused(refusal, problems))
            finish()
        }
    }

    /**
     * Whether [path] is one only a signed-in person may reach: under the API, and not plainly one of a
     * public module's.
     */
    private fun closed(path: String): Boolean =
        path.startsWith("$VERSIONED/") && !(isPlain(path) && openPrefixes.any { path == it || path.startsWith("$it/") })

    private fun isPlain(path: String): Boolean {
        val segments = path.removePrefix("/").split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." } && '%' !in path && '\\' !in path
    }

    /**
     * What a route that needs a signed-in person says to each kind of caller: nothing, or why not.
     */
    private class Admitting : Caller.Report<AccessFailure?> {
        override fun signedIn(account: kotlin.uuid.Uuid): AccessFailure? = null

        override fun lapsed(): AccessFailure = AccessFailure.SessionExpired

        override fun anonymous(): AccessFailure = AccessFailure.SignInRequired
    }

    private companion object {
        const val SEC_FETCH_SITE = "Sec-Fetch-Site"

        const val SAME_ORIGIN = "same-origin"
    }
}

internal const val VERSIONED = "/api/v1"
