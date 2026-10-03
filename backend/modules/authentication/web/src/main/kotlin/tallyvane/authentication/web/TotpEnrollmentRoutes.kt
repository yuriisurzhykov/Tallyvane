package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.BeginTotpUseCase
import tallyvane.authentication.application.TotpBegun
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.SecretAnswer

/**
 * Beginning to turn TOTP on (ADR-093).
 *
 * ```
 * POST /api/v1/totp-enrollments   the key to put in an authenticator app, told once
 * ```
 *
 * A dangerous act, so the edge asks for a recent proof of who the person is first (ADR-092): a second
 * factor set up by someone else locks the owner out. The answer carries the seed, so it is never stored
 * for a repeat of its `Idempotency-Key`, and never cached.
 */
internal class TotpEnrollmentRoutes(private val begin: BeginTotpUseCase, private val problems: TotpBeginProblems) :
    RouteModule {
    override val basePath: BasePath = BasePath("/totp-enrollments")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.post {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = begin.begin(AccountId(Requester(call).account()))) {
                is TotpBegun.Started -> {
                    SecretAnswer(call).withheldFromReplay()
                    call.respond(startedBy(outcome))
                }
                is TotpBegun.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun startedBy(outcome: TotpBegun.Started): TotpStarted {
        val told = mutableListOf<TotpStarted>()
        outcome.writeTo { key, uri -> told += TotpStarted(key.revealed(), uri.revealed()) }
        return told.single()
    }

    override fun toString(): String = "TotpEnrollmentRoutes"
}
