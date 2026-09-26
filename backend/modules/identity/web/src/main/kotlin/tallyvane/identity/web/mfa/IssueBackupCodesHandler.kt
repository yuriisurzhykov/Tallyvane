package tallyvane.identity.web.mfa

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.IssueBackupCodesUseCase
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

internal class IssueBackupCodesHandler(
    private val issue: IssueBackupCodesUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val authenticationProblems: AuthenticationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/backup-codes") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val issued = issue.issue(
                identity.userId, identity.sessionId, call.request.headers["X-Action-Proof"],
            )
            if (issued == null) {
                call.respond(Refused(AuthenticationFailure.InvalidCredential, authenticationProblems))
            } else {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(HttpStatusCode.OK, IssueBackupCodesResponseBody(issued.map(Secret::revealed)))
            }
        }
    }
}
