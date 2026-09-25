package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

internal class AuthenticationPolicyAdministration(
    private val users: UserRepository,
    private val policies: AuthenticationPolicyStore,
    private val audit: AuthenticationPolicyAuditStore,
    adminEmails: Set<String>,
    private val clock: Clock,
    private val transactions: TransactionRunner,
) {
    private val admins = adminEmails.mapTo(mutableSetOf()) { it.trim().lowercase() }

    suspend fun read(actor: UserId): AuthenticationPolicyResult = transactions.inTransaction {
        if (!authorized(actor)) {
            audit.record(actor, "POLICY_READ_DENIED", policies.current()?.version ?: 0, clock.now())
            Verdict.Commit(AuthenticationPolicyResult.Forbidden)
        } else {
            Verdict.Commit(AuthenticationPolicyResult.Policy(policies.current() ?: AuthenticationPolicy.defaults()))
        }
    }

    suspend fun update(
        actor: UserId,
        expectedVersion: Long,
        rules: List<AuthenticationRule>,
        advancedAcknowledged: Boolean,
    ): AuthenticationPolicyResult = transactions.inTransaction {
        val current = policies.current() ?: AuthenticationPolicy.defaults()
        if (!authorized(actor)) {
            audit.record(actor, "POLICY_UPDATE_DENIED", current.version, clock.now())
            Verdict.Commit(AuthenticationPolicyResult.Forbidden)
        } else if (current.version != expectedVersion) {
            audit.record(actor, "POLICY_UPDATE_CONFLICT", current.version, clock.now())
            Verdict.Commit(AuthenticationPolicyResult.Conflict)
        } else {
            val replacement = try {
                AuthenticationPolicy(expectedVersion + 1, rules, advancedAcknowledged)
            } catch (_: IllegalArgumentException) {
                audit.record(actor, "POLICY_UPDATE_INVALID", current.version, clock.now())
                return@inTransaction Verdict.Commit(AuthenticationPolicyResult.Invalid)
            }
            if (!policies.replace(expectedVersion, replacement)) {
                audit.record(actor, "POLICY_UPDATE_CONFLICT", current.version, clock.now())
                Verdict.Commit(AuthenticationPolicyResult.Conflict)
            } else {
                audit.record(actor, "POLICY_UPDATED", replacement.version, clock.now())
                Verdict.Commit(AuthenticationPolicyResult.Policy(replacement))
            }
        }
    }

    private suspend fun authorized(actor: UserId): Boolean {
        val user = users.findById(actor) ?: return false
        return user.disabledAt == null && user.emailVerified && user.email.value.lowercase() in admins
    }
}
