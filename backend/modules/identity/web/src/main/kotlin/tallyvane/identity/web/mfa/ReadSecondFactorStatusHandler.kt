package tallyvane.identity.web.mfa

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.secondfactor.ReadSecondFactorStatusUseCase
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal

internal class ReadSecondFactorStatusHandler(
    private val read: ReadSecondFactorStatusUseCase,
    private val current: CurrentPrincipal,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/mfa/status") {
            val identity = current.resolve(call) ?: return@get
            val status = read.read(identity.userId, identity.sessionId)
            call.respond(
                SecondFactorStatusBody(
                    enrolled = status.enrolled.map(SecondFactorKind::name).sorted(),
                    recentlyAuthenticated = status.recentlyAuthenticated,
                ),
            )
        }
    }
}
