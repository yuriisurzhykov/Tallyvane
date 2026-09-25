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
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationScheme
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind

/**
 * [AuthenticationPolicyStore] backed by the singleton policy row and its normalized rules.
 */
internal class AuthenticationPolicyStoreOverExposed : AuthenticationPolicyStore {
    override suspend fun current(): AuthenticationPolicy? {
        val metadata = AuthenticationPolicyTable.selectAll()
            .where { AuthenticationPolicyTable.id eq POLICY_ID }
            .singleOrNull() ?: return null
        val schemes = AuthenticationPolicySchemesTable.selectAll().map { it.toScheme() }
        return AuthenticationPolicy.fromSchemes(
            metadata[AuthenticationPolicyTable.version],
            schemes,
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
        AuthenticationPolicySchemesTable.deleteWhere { AuthenticationPolicySchemesTable.id neq "" }
        policy.schemes.forEach { scheme ->
            AuthenticationPolicySchemesTable.insert {
                it[id] = scheme.id
                it[action] = scheme.action.name
                it[requiredTokens] = scheme.requiredTokens.map(AuthenticationTokenKind::name)
                it[assuranceRank] = scheme.assuranceRank
                it[enabled] = scheme.enabled
            }
        }
        return true
    }

    private fun ResultRow.toScheme(): AuthenticationScheme = AuthenticationScheme(
        id = this[AuthenticationPolicySchemesTable.id],
        action = AuthenticationAction.valueOf(this[AuthenticationPolicySchemesTable.action]),
        requiredTokens = this[AuthenticationPolicySchemesTable.requiredTokens].map {
            AuthenticationTokenKind.valueOf(it)
        }.toSet(),
        assuranceRank = this[AuthenticationPolicySchemesTable.assuranceRank],
        enabled = this[AuthenticationPolicySchemesTable.enabled],
    )

    private companion object {
        const val POLICY_ID: Short = 1
    }
}
