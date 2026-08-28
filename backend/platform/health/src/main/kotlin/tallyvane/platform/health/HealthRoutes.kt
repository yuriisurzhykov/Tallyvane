package tallyvane.platform.health

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import tallyvane.platform.observability.health.HealthReporter

/**
 * The three health addresses (§11.3), and the only place they are decided.
 *
 * ```
 * GET /api/v1/health         the breakdown for a human or an alert; 200 always
 * GET /api/v1/health/live    is the process alive; touches nothing
 * GET /api/v1/health/ready   should traffic come here; 200 or 503
 * ```
 *
 * ### Why liveness asks nothing
 *
 * It answers because it answered. A liveness probe that consults a dependency turns that
 * dependency's outage into a restart loop of a healthy process: the database goes down, the probe
 * fails, the orchestrator restarts an application that was working, repeatedly, and the outage now
 * has two causes. So this handler has no reporter to consult even by accident — it is not given one.
 *
 * It also makes the most frequent probe free. Measured in `playground/health`: an aggregate costs
 * 85 ms for the database plus 147 ms for the schema warm, and an orchestrator asks every few
 * seconds.
 *
 * ### Why readiness answers with a code and the aggregate with a body
 *
 * `ready` is computed from `requiredForReadiness`, not from the aggregate status: an unavailable
 * model must not close the two thirds of the product that needs no model. So readiness is 200 or
 * 503 and carries `status` only, while `/health` is read by a human and always answers 200 — an
 * informational endpoint that returned 503 would look to a monitor like the endpoint itself being
 * down.
 *
 * ### Why nothing here is cacheable
 *
 * `Cache-Control: no-store` on all three. Cloudflare sits in front of this application (§16.3), and
 * a 200 with no cache directives is a legitimate thing to cache — which would eventually mean a
 * cheerful "up" served from a cache while the application is on the floor.
 */
public class HealthRoutes(private val reporter: HealthReporter, private val token: ServiceToken) : RouteModule {

    override val basePath: BasePath = BasePath("/health")

    private val presented = Presented()

    override fun install(route: Route) {
        route.get {
            val report = reporter.report()
            call.uncached()
            if (token.admits(call.request.headers[SERVICE_HEADER])) {
                call.respond(presented.detail(report))
            } else {
                call.respond(presented.summary(report))
            }
        }

        route.get("/live") {
            call.uncached()
            call.respond(Summary(status = "up"))
        }

        route.get("/ready") {
            val report = reporter.report()
            call.uncached()
            val code = if (report.ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(
                status = code,
                message = presented.summary(report),
            )
        }
    }

    private fun ApplicationCall.uncached() {
        response.header(
            name = HttpHeaders.CacheControl,
            value = "no-store",
        )
    }

    private companion object {
        /**
         * Not `Authorization`: this is not a user's credential, and using that header would invite
         * a future reader to treat it as one. ADR-063 explains the whole arrangement.
         *
         * No product name in it either — `no-hardcoded-product-name` caught the first spelling, and
         * the rule is right: a rename would have had to reach a wire contract.
         */
        const val SERVICE_HEADER = "X-Service-Token"
    }
}
