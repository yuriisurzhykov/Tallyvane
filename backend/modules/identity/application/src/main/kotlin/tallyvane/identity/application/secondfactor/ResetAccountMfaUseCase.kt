package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Administrative MFA reset. It revokes sessions and every sign-in challenge before returning.
 */
public interface ResetAccountMfaUseCase : UseCase {
    public suspend fun reset(request: Request): Outcome

    public data class Request(public val actor: UserId, public val targetEmail: Email, public val confirmed: Boolean)

    public enum class Outcome { RESET, FORBIDDEN, NOT_FOUND, CONFIRMATION_REQUIRED }

    public class Reset internal constructor(
        private val users: UserRepository,
        private val sessions: SessionStore,
        private val refreshTokens: RefreshTokenStore,
        private val pending: PendingAuthenticationStore,
        private val totp: TotpEnrollmentStore,
        private val emailMfa: EmailMfaEnrollmentStore,
        private val backupCodes: BackupCodeStore,
        private val policies: AuthenticationPolicyStore,
        private val audit: AuthenticationPolicyAuditStore,
        adminEmails: Set<String>,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : ResetAccountMfaUseCase {
        private val admins = adminEmails.mapTo(mutableSetOf()) { it.trim().lowercase() }

        override suspend fun reset(request: Request): Outcome = transactions.inTransaction {
            val actor = users.findById(request.actor)
            val allowed =
                actor != null &&
                    actor.disabledAt == null &&
                    actor.emailVerified &&
                    actor.email.value.lowercase() in admins
            if (!allowed) return@inTransaction Verdict.Commit(Outcome.FORBIDDEN)
            if (!request.confirmed) return@inTransaction Verdict.Commit(Outcome.CONFIRMATION_REQUIRED)
            val target =
                users.findByEmail(request.targetEmail) ?: return@inTransaction Verdict.Commit(Outcome.NOT_FOUND)
            val now = clock.now()
            val activeSessions = sessions.listFor(target.id).filter { it.revokedAt == null }
            sessions.revokeAllFor(target.id, now)
            activeSessions.forEach { refreshTokens.revokeAllFor(it.id) }
            pending.deleteFor(target.id)
            totp.delete(target.id)
            emailMfa.unenroll(target.id)
            backupCodes.replace(target.id, emptyList())
            audit.record(
                request.actor,
                "MFA_RESET:${target.id.value}",
                policies.current()?.version ?: AuthenticationPolicy.defaults().version,
                now,
            )
            Verdict.Commit(Outcome.RESET)
        }
    }
}
