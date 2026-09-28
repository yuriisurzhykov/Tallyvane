package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TotpEnrollmentStore
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
        private val policies: AuthenticationPolicyStore,
        private val clock: Clock,
        private val transactions: TransactionRunner,
        private val actionProofs: AuthenticationActionProofRequirement? = null,
    ) : DisableSecondFactorUseCase {
        override suspend fun disable(request: Request): Outcome = transactions.inTransaction {
            when (val refusal = refusal(request)) {
                null -> {
                    removeEnrollment(request)
                    Verdict.Commit(Outcome.DISABLED)
                }
                else -> Verdict.Rollback(refusal)
            }
        }

        private suspend fun refusal(request: Request): Outcome? = when {
            !authorized(request) -> Outcome.REAUTHENTICATION_REQUIRED
            !request.confirmed -> Outcome.CONFIRMATION_REQUIRED
            else -> policyRefusal(request)
        }

        private suspend fun authorized(request: Request): Boolean =
            sessions.find(request.sessionId)?.let { it.userId == request.userId && it.revokedAt == null } == true &&
                actionProofs?.consume(
                    request.actionProof,
                    request.userId,
                    request.sessionId,
                    AuthenticationAction.MANAGE_SECOND_FACTORS,
                ) == true

        private suspend fun policyRefusal(request: Request): Outcome? {
            val enrolled = enrolled(request.userId)
            if (request.kind !in enrolled) return Outcome.NOT_ENROLLED
            val remaining = enrolled - request.kind
            val allowed = policies.current()?.let { policy -> canCompleteSignIn(policy, remaining) } == true
            return if (allowed) null else Outcome.REQUIRED_BY_POLICY
        }

        private fun canCompleteSignIn(
            policy: tallyvane.identity.domain.secondfactor.AuthenticationPolicy,
            remaining: Set<SecondFactorKind>,
        ): Boolean {
            if (remaining.isEmpty()) return true
            val remainingTokens = remaining.mapTo(mutableSetOf(), ::toToken)
            return policy.schemesFor(AuthenticationAction.SIGN_IN).any { scheme ->
                scheme.requiredTokens.any(AuthenticationTokenKind::isSecondFactor) &&
                    scheme.requiredTokens.any(remainingTokens::contains)
            }
        }

        private suspend fun removeEnrollment(request: Request) {
            when (request.kind) {
                SecondFactorKind.TOTP -> totp.delete(request.userId)
                SecondFactorKind.EMAIL_OTP -> emailMfa.unenroll(request.userId)
            }
        }

        private suspend fun enrolled(userId: UserId): Set<SecondFactorKind> = buildSet {
            if (totp.find(userId)?.active == true) add(SecondFactorKind.TOTP)
            if (emailMfa.isEnrolled(userId)) add(SecondFactorKind.EMAIL_OTP)
        }

        private fun toToken(kind: SecondFactorKind): AuthenticationTokenKind = when (kind) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
        }
    }
}
