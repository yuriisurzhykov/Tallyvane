package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

public interface BeginEmailMfaEnrollmentUseCase : UseCase {
    public suspend fun begin(userId: UserId, sessionId: SessionId, actionProof: String?): Uuid?

    public class Begin(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
        private val actionProofs: AuthenticationActionProofRequirement?,
    ) : BeginEmailMfaEnrollmentUseCase {
        override suspend fun begin(userId: UserId, sessionId: SessionId, actionProof: String?): Uuid? {
            val email = transactions.inTransaction {
                val user = users.findById(userId)
                val authorized = actionProofs?.consume(
                    actionProof, userId, sessionId, AuthenticationAction.MANAGE_SECOND_FACTORS,
                ) == true
                when {
                    !authorized -> Verdict.Rollback(null)
                    user == null -> Verdict.Rollback(null)
                    user.disabledAt != null -> Verdict.Rollback(null)
                    !user.emailVerified -> Verdict.Rollback(null)
                    else -> Verdict.Commit(user.email)
                }
            } ?: return null
            val challenge = challenges.issue(email, EmailChallengePurpose.MFA, enrollmentBinding(userId))
            return challenge?.id
        }
    }

    public companion object {
        public fun enrollmentBinding(userId: UserId): String = "email-mfa-enrollment:${userId.value}"
    }
}
