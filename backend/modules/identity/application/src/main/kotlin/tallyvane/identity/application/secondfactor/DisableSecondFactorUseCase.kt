package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.minutes

public interface DisableSecondFactorUseCase : UseCase {
    public suspend fun disable(request: Request): Outcome

    public data class Request(
        public val userId: UserId,
        public val sessionId: SessionId,
        public val kind: SecondFactorKind,
        public val confirmed: Boolean,
    )

    public enum class Outcome {
        DISABLED,
        NOT_ENROLLED,
        REAUTHENTICATION_REQUIRED,
        REQUIRED_BY_POLICY,
        CONFIRMATION_REQUIRED,
    }

    public class Disable(
        private val sessions: SessionStore,
        private val totp: TotpEnrollmentStore,
        private val emailMfa: EmailMfaEnrollmentStore,
        private val backupCodes: BackupCodeStore,
        private val policies: AuthenticationPolicyStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : DisableSecondFactorUseCase {
        override suspend fun disable(request: Request): Outcome = transactions.inTransaction {
            val now = clock.now()
            val session = sessions.find(request.sessionId)
            if (session?.userId != request.userId ||
                session.revokedAt != null ||
                !isRecentlyAuthenticated(session.reauthenticatedAt, now)
            ) {
                return@inTransaction Verdict.Rollback(Outcome.REAUTHENTICATION_REQUIRED)
            }
            if (!request.confirmed) return@inTransaction Verdict.Rollback(Outcome.CONFIRMATION_REQUIRED)

            val enrolled = enrolled(request.userId)
            if (request.kind !in enrolled) return@inTransaction Verdict.Rollback(Outcome.NOT_ENROLLED)
            val policy = policies.current() ?: AuthenticationPolicy.defaults()
            val remaining = enrolled - request.kind
            val leavesRequiredSchemeUnusable = policy.rules.values.any { rule ->
                rule.enabled &&
                    rule.requirement == MfaRequirement.REQUIRED &&
                    rule.available(remaining, policy.advancedAcknowledged).isEmpty()
            }
            if (leavesRequiredSchemeUnusable) return@inTransaction Verdict.Rollback(Outcome.REQUIRED_BY_POLICY)

            when (request.kind) {
                SecondFactorKind.TOTP -> totp.delete(request.userId)
                SecondFactorKind.EMAIL_OTP -> emailMfa.unenroll(request.userId)
                SecondFactorKind.BACKUP_CODE -> backupCodes.replace(request.userId, emptyList())
            }
            Verdict.Commit(Outcome.DISABLED)
        }

        private suspend fun enrolled(userId: UserId): Set<SecondFactorKind> = buildSet {
            if (totp.find(userId)?.active == true) add(SecondFactorKind.TOTP)
            if (emailMfa.isEnrolled(userId)) add(SecondFactorKind.EMAIL_OTP)
            if (backupCodes.hasAny(userId)) add(SecondFactorKind.BACKUP_CODE)
        }

        private fun isRecentlyAuthenticated(authenticatedAt: kotlin.time.Instant?, now: kotlin.time.Instant): Boolean =
            authenticatedAt != null && authenticatedAt <= now && now - authenticatedAt <= REAUTHENTICATION_WINDOW

        private companion object {
            val REAUTHENTICATION_WINDOW = 5.minutes
        }
    }
}
