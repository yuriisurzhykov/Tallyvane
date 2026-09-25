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
            return userMatches(user.email, email) &&
                verifies(userId, challengeId, email, code) &&
                markVerified(userId)
        }

        private fun userMatches(accountEmail: Email, requestedEmail: Email): Boolean =
            accountEmail.value.equals(requestedEmail.value, ignoreCase = true)

        private suspend fun verifies(userId: UserId, challengeId: Uuid, email: Email, code: Secret): Boolean =
            challenges.verify(
                challengeId,
                email,
                EmailChallengePurpose.REGISTRATION,
                code,
                userId.value.toString(),
            )

        private suspend fun markVerified(userId: UserId): Boolean =
            transactions.inTransaction { Verdict.Commit(users.markEmailVerified(userId)) }
    }
}
