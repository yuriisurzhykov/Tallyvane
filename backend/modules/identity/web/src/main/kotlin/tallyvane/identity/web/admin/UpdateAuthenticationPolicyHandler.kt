package tallyvane.identity.web.admin

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.identity.application.secondfactor.AuthenticationPolicyResult
import tallyvane.identity.application.secondfactor.UpdateAuthenticationPolicyUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused

internal class UpdateAuthenticationPolicyHandler(
    private val update: UpdateAuthenticationPolicyUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val problems: AuthenticationPolicyProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.put("/admin/policy") {
            val identity = currentPrincipal.resolve(call) ?: return@put
            val body = call.receive<UpdateAuthenticationPolicyBody>()
            val rules = body.rules.toDomainOrNull()
            if (rules == null || body.expectedVersion < 1) {
                call.respond(Refused(AuthenticationPolicyFailure.Invalid, problems))
                return@put
            }
            when (val result = update.update(identity.userId, body.expectedVersion, rules, body.advancedAcknowledged)) {
                is AuthenticationPolicyResult.Policy -> call.respond(result.value.toBody())
                AuthenticationPolicyResult.Forbidden -> call.respond(
                    Refused(AuthenticationPolicyFailure.Forbidden, problems),
                )
                AuthenticationPolicyResult.Conflict -> call.respond(
                    Refused(AuthenticationPolicyFailure.Conflict, problems),
                )
                AuthenticationPolicyResult.Invalid -> call.respond(
                    Refused(AuthenticationPolicyFailure.Invalid, problems),
                )
            }
        }
    }

    private fun List<AuthenticationPolicyRuleBody>.toDomainOrNull(): List<AuthenticationRule>? = runCatching {
        map { rule ->
            AuthenticationRule(
                PrimaryMethod.valueOf(rule.primary),
                rule.enabled,
                MfaRequirement.valueOf(rule.requirement),
                rule.allowedMethods.map(SecondFactorKind::valueOf).toSet(),
            )
        }
    }.getOrNull()

    private fun AuthenticationPolicy.toBody(): AuthenticationPolicyBody = AuthenticationPolicyBody(
        version,
        PrimaryMethod.entries.mapNotNull { rules[it] }.map { rule ->
            AuthenticationPolicyRuleBody(
                rule.primary.name,
                rule.enabled,
                rule.requirement.name,
                rule.allowedMethods.map(SecondFactorKind::name),
            )
        },
        advancedAcknowledged,
    )
}
