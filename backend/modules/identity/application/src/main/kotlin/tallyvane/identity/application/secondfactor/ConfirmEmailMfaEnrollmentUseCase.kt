package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

public interface ConfirmEmailMfaEnrollmentUseCase : UseCase {
    public suspend fun confirm(
        userId: UserId,
        challengeId: Uuid,
        code: Secret,
    ): Boolean

    public class Confirm(
        private val users: UserRepository,
        private val enrollment: EmailMfaEnrollmentStore,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
    ) : ConfirmEmailMfaEnrollmentUseCase {
        override suspend fun confirm(
            userId: UserId,
            challengeId: Uuid,
            code: Secret,
        ): Boolean =
            transactions.inTransaction {
                val user = users.findById(userId)
                val accepted = user != null &&
                    user.disabledAt == null &&
                    user.emailVerified &&
                    challenges.verifyInCurrentTransaction(
                        challengeId,
                        user.email,
                        EmailChallengePurpose.MFA,
                        code,
                        BeginEmailMfaEnrollmentUseCase.enrollmentBinding(userId),
                    )
                if (accepted) {
                    enrollment.enroll(userId)
                    Verdict.Commit(true)
                } else {
                    Verdict.Commit(false)
                }
            }
    }
}
