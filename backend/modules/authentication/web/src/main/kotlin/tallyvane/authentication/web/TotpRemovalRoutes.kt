package tallyvane.authentication.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import tallyvane.authentication.application.DisableTotpUseCase
import tallyvane.authentication.application.TotpDisabled
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

/**
 * Turning TOTP off (ADR-093).
 *
 * ```
 * DELETE /api/v1/totp-enrollment   removes the seed and the recovery codes; `409` if there is none
 * ```
 *
 * A dangerous act, so the edge asks for a recent proof of who the person is first (ADR-092).
 */
internal class TotpRemovalRoutes(private val disable: DisableTotpUseCase, private val problems: TotpRemovalProblems) :
    RouteModule {
    override val basePath: BasePath = BasePath("/totp-enrollment")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.delete {
            val requester = Requester(call)
            when (val outcome = disable.disable(AccountId(requester.account()), requester.session())) {
                is TotpDisabled.Disabled -> call.respond(HttpStatusCode.NoContent)
                is TotpDisabled.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "TotpRemovalRoutes"
}
