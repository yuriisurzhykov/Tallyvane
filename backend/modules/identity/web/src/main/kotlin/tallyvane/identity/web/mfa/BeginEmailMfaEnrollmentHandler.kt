package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.BeginEmailMfaEnrollmentUseCase
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.EmailChallengeResponseBody
import tallyvane.platform.http.Refused

internal class BeginEmailMfaEnrollmentHandler(
    private val begin: BeginEmailMfaEnrollmentUseCase,
    private val currentPrincipal: CurrentPrincipal,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/email/enroll") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val challengeId = begin.begin(
                identity.userId, identity.sessionId, call.request.headers["X-Action-Proof"],
            )
            if (challengeId == null) {
                call.respond(Refused(SecondFactorFailure.ReauthenticationRequired, SecondFactorProblems()))
            } else {
                call.respond(HttpStatusCode.Accepted, EmailChallengeResponseBody(challengeId.toString()))
            }
        }
    }
}
