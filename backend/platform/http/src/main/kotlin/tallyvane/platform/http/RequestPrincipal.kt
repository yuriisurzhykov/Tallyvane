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
            resolver.resolve(call.request.cookies[COOKIE_NAME])?.let { call.attributes.put(KEY, it) }
            proceed()
        }
    }

    public companion object {
        /**
         * Public so the composition root's own cookie-writing code (`identity`'s sign-in/refresh
         * routes) names the exact same cookie [install]'s interceptor reads back — one constant,
         * not two literals that could drift.
         */
        public const val COOKIE_NAME: String = "session"

        private val KEY = AttributeKey<Any>("tallyvane.principal")

        /**
         * What [install]'s interceptor resolved for this call, `null` otherwise —
         * `identity`'s own `ResolvedPrincipal`, at the one call site both sides are visible from
         * the composition root, cast back to its real type.
         */
        public fun of(call: ApplicationCall): Any? = call.attributes.getOrNull(KEY)
    }
}
