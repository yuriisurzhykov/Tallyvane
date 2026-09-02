package tallyvane.platform.http

import io.ktor.server.application.*
import io.ktor.util.*
import tallyvane.platform.http.RequestPrincipal.Companion.of

/**
 * Runs [resolver] before every route and makes its answer readable back off the call — the
 * mechanism the design calls for: "run this before every route, store the result in a call
 * attribute", the same one [Api] already uses to carry a trace id through the pipeline.
 *
 * A class of its own rather than a fourth [Api] constructor parameter: principal resolution and
 * [tallyvane.platform.http.csrf.CsrfGuard] are wired the same way, through the same composition
 * root, but as independent interceptors — two separate concerns, not one combined pipeline
 * object mounting everything `identity` needs.
 */
public class RequestPrincipal(private val resolver: RequestPrincipalResolver) {
    public fun install(application: Application) {
        application.intercept(ApplicationCallPipeline.Setup) {
            resolver.resolve(call.request.cookies[COOKIE])?.let { call.attributes.put(KEY, it) }
            proceed()
        }
    }

    public companion object {
        /**
         * Named here rather than left for each caller to spell: a session-scoped route reads it
         * back with [of], and both sides must agree on the same key.
         */
        private const val COOKIE = "session"

        private val KEY = AttributeKey<Any>("tallyvane.principal")

        /**
         * What [install]'s interceptor resolved for this call, `null` otherwise —
         * `identity`'s own `ResolvedPrincipal`, at the one call site both sides are visible from
         * the composition root, cast back to its real type.
         */
        public fun of(call: ApplicationCall): Any? = call.attributes.getOrNull(KEY)
    }
}
