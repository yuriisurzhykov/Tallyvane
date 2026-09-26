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

    public suspend fun verify(challengeId: Uuid, email: Email, code: Secret): Boolean

    public class Verify(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
    ) : VerifyRegistrationEmailUseCase {
        override suspend fun verify(challengeId: Uuid, email: Email, code: Secret): Boolean {
            val challenge = challenges.challenge(challengeId) ?: return false
            if (challenge.purpose != EmailChallengePurpose.REGISTRATION || !userMatches(challenge.email, email)) return false
            val userId = runCatching { UserId(Uuid.parse(challenge.binding)) }.getOrNull() ?: return false
            val user = transactions.inTransaction { Verdict.Commit(users.findById(userId)) } ?: return false
            return user.disabledAt == null && !user.emailVerified && userMatches(user.email, email) &&
                challenges.verify(challengeId, email, EmailChallengePurpose.REGISTRATION, code, challenge.binding) &&
                markVerified(userId)
        }

        private fun userMatches(accountEmail: Email, requestedEmail: Email): Boolean =
            accountEmail.value.equals(requestedEmail.value, ignoreCase = true)

        private suspend fun markVerified(userId: UserId): Boolean =
            transactions.inTransaction { Verdict.Commit(users.markEmailVerified(userId)) }
    }
}
