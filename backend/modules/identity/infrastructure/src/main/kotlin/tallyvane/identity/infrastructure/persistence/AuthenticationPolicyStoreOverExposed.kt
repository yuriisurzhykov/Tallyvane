package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind

/**
 * [AuthenticationPolicyStore] backed by the singleton policy row and its normalized rules.
 */
internal class AuthenticationPolicyStoreOverExposed : AuthenticationPolicyStore {
    override suspend fun current(): AuthenticationPolicy? {
        val metadata = AuthenticationPolicyTable.selectAll()
            .where { AuthenticationPolicyTable.id eq POLICY_ID }
            .singleOrNull() ?: return null
        val rules = AuthenticationPolicyRulesTable.selectAll().map { it.toRule() }
        return AuthenticationPolicy(
            metadata[AuthenticationPolicyTable.version],
            rules,
            metadata[AuthenticationPolicyTable.advancedAcknowledged],
        )
    }

    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
        require(policy.version == expectedVersion + 1) { "Policy versions must advance exactly once" }
        val changed = AuthenticationPolicyTable.update({
            (AuthenticationPolicyTable.id eq POLICY_ID) and (AuthenticationPolicyTable.version eq expectedVersion)
        }) {
            it[version] = policy.version
            it[advancedAcknowledged] = policy.advancedAcknowledged
        }
        if (changed != 1) return false
        AuthenticationPolicyRulesTable.deleteWhere { AuthenticationPolicyRulesTable.primaryMethod neq "" }
        policy.rules.values.forEach { rule ->
            AuthenticationPolicyRulesTable.insert {
                it[primaryMethod] = rule.primary.name
                it[enabled] = rule.enabled
                it[requirement] = rule.requirement.name
                it[allowedMethods] = rule.allowedMethods.map(SecondFactorKind::name)
            }
        }
        return true
    }

    private fun ResultRow.toRule(): AuthenticationRule = AuthenticationRule(
        primary = PrimaryMethod.valueOf(this[AuthenticationPolicyRulesTable.primaryMethod]),
        enabled = this[AuthenticationPolicyRulesTable.enabled],
        requirement = MfaRequirement.valueOf(this[AuthenticationPolicyRulesTable.requirement]),
        allowedMethods = this[AuthenticationPolicyRulesTable.allowedMethods].map(SecondFactorKind::valueOf).toSet(),
    )

    private companion object {
        const val POLICY_ID: Short = 1
    }
}
