package tallyvane.identity.application.email

import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

public interface VerifyRegistrationEmailUseCase : UseCase {

    public suspend fun verify(userId: UserId, challengeId: Uuid, email: Email, code: Secret): Boolean

    public class Verify(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
    ) : VerifyRegistrationEmailUseCase {
        override suspend fun verify(userId: UserId, challengeId: Uuid, email: Email, code: Secret): Boolean {
            val user = transactions.inTransaction { Verdict.Commit(users.findById(userId)) } ?: return false
            if (!user.email.value.equals(email.value, ignoreCase = true)) return false
            if (!challenges.verify(
                    challengeId,
                    email,
                    EmailChallengePurpose.REGISTRATION,
                    code,
                    userId.value.toString(),
                )
            ) return false
            return transactions.inTransaction { Verdict.Commit(users.markEmailVerified(userId)) }
        }
    }
}
