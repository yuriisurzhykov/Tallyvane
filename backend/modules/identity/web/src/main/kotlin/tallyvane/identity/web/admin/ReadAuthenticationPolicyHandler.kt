package tallyvane.identity.web.admin

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.secondfactor.AuthenticationPolicyResult
import tallyvane.identity.application.secondfactor.ReadAuthenticationPolicyUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused

internal class ReadAuthenticationPolicyHandler(
    private val read: ReadAuthenticationPolicyUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val problems: AuthenticationPolicyProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/admin/policy") {
            val identity = currentPrincipal.resolve(call) ?: return@get
            when (val result = read.read(identity.userId)) {
                is AuthenticationPolicyResult.Policy -> call.respond(result.value.toBody())
                AuthenticationPolicyResult.Forbidden -> call.respond(
                    Refused(AuthenticationPolicyFailure.Forbidden, problems),
                )
                else -> call.respond(Refused(AuthenticationPolicyFailure.Invalid, problems))
            }
        }
    }

    private fun AuthenticationPolicy.toBody(): AuthenticationPolicyBody = AuthenticationPolicyBody(
        version = version,
        schemes = schemes.map { scheme ->
            AuthenticationSchemeBody(
                scheme.id,
                scheme.action.name,
                scheme.requiredTokens.map(AuthenticationTokenKind::name),
                scheme.assuranceRank,
                scheme.enabled,
            )
        },
        advancedAcknowledged = advancedAcknowledged,
        rules = PrimaryMethod.entries.mapNotNull { rules[it] }.map { rule ->
            AuthenticationPolicyRuleBody(
                rule.primary.name,
                rule.enabled,
                rule.requirement.name,
                rule.allowedMethods.map(SecondFactorKind::name),
            )
        },
    )
}
