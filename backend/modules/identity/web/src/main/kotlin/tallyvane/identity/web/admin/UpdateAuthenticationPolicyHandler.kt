package tallyvane.identity.web.admin

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.identity.application.secondfactor.AuthenticationPolicyResult
import tallyvane.identity.application.secondfactor.UpdateAuthenticationPolicyUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.AuthenticationScheme
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
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
        route.put("/policy") {
            val identity = currentPrincipal.resolve(call) ?: return@put
            val body = call.receive<UpdateAuthenticationPolicyBody>()
            val schemes = body.schemes.toSchemesOrNull()
            val rules = body.rules.toDomainOrNull()
            if (body.hasInvalidPolicy(schemes, rules)) {
                call.respond(Refused(AuthenticationPolicyFailure.Invalid, problems))
                return@put
            }
            val change = if (body.schemes.isNotEmpty()) {
                UpdateAuthenticationPolicyUseCase.Change.Schemes(requireNotNull(schemes))
            } else {
                UpdateAuthenticationPolicyUseCase.Change.Rules(requireNotNull(rules))
            }
            val result = update.update(identity.userId, body.expectedVersion, change, body.advancedAcknowledged)
            when (result) {
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

    private fun UpdateAuthenticationPolicyBody.hasInvalidPolicy(
        schemes: List<AuthenticationScheme>?,
        rules: List<AuthenticationRule>?,
    ): Boolean {
        if (expectedVersion < 1) return true
        return when {
            this.schemes.isNotEmpty() -> schemes == null
            rules.isNullOrEmpty() -> true
            else -> false
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

    private fun List<AuthenticationSchemeBody>.toSchemesOrNull(): List<AuthenticationScheme>? = runCatching {
        map { scheme ->
            AuthenticationScheme(
                id = scheme.id,
                action = AuthenticationAction.valueOf(scheme.action),
                requiredTokens = scheme.requiredTokens.map(AuthenticationTokenKind::valueOf).toSet(),
                assuranceRank = scheme.assuranceRank,
                enabled = scheme.enabled,
            )
        }
    }.getOrNull()

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
