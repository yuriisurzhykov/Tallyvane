package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface DisableSecondFactorUseCase : UseCase {
    public suspend fun disable(request: Request): Outcome

    public data class Request(
        public val userId: UserId,
        public val sessionId: SessionId,
        public val kind: SecondFactorKind,
        public val confirmed: Boolean,
        public val actionProof: String? = null,
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
        private val actionProofs: AuthenticationActionProofRequirement? = null,
    ) : DisableSecondFactorUseCase {
        override suspend fun disable(request: Request): Outcome = transactions.inTransaction {
            val activeSession = sessions.find(request.sessionId)?.let {
                it.userId == request.userId && it.revokedAt == null
            } == true
            val authorized = activeSession && actionProofs?.consume(
                request.actionProof, request.userId, request.sessionId, AuthenticationAction.MANAGE_SECOND_FACTORS,
            ) == true
            if (!authorized) {
                return@inTransaction Verdict.Rollback(Outcome.REAUTHENTICATION_REQUIRED)
            }
            if (!request.confirmed) return@inTransaction Verdict.Rollback(Outcome.CONFIRMATION_REQUIRED)

            val enrolled = enrolled(request.userId)
            if (request.kind !in enrolled) return@inTransaction Verdict.Rollback(Outcome.NOT_ENROLLED)
            val policy = policies.current() ?: AuthenticationPolicy.defaults()
            val remaining = enrolled - request.kind
            if (remaining.isNotEmpty()) {
                val remainingTokens = remaining.mapTo(mutableSetOf(), ::toToken)
                val canCompleteSignIn = policy.schemesFor(AuthenticationAction.SIGN_IN).any { scheme ->
                    scheme.requiredTokens.any(AuthenticationTokenKind::isSecondFactor) &&
                        scheme.requiredTokens.any(remainingTokens::contains)
                }
                if (!canCompleteSignIn) return@inTransaction Verdict.Rollback(Outcome.REQUIRED_BY_POLICY)
            }

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

        private fun toToken(kind: SecondFactorKind): AuthenticationTokenKind = when (kind) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
            SecondFactorKind.BACKUP_CODE -> AuthenticationTokenKind.BACKUP_CODE
        }
    }
}
