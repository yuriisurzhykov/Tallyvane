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
        override suspend fun verify(challengeId: Uuid, email: Email, code: Secret): Boolean =
            registration(challengeId, email)?.let { pending ->
                challenges.verify(
                    challengeId,
                    email,
                    EmailChallengePurpose.REGISTRATION,
                    code,
                    pending.binding,
                ) &&
                    markVerified(pending.userId)
            } ?: false

        private suspend fun registration(challengeId: Uuid, email: Email): PendingRegistration? =
            challenges.challenge(challengeId)
                ?.takeIf { it.purpose == EmailChallengePurpose.REGISTRATION && userMatches(it.email, email) }
                ?.let { challenge ->
                    runCatching { UserId(Uuid.parse(challenge.binding)) }.getOrNull()?.let { userId ->
                        transactions.inTransaction { Verdict.Commit(users.findById(userId)) }
                            ?.takeIf { user ->
                                user.disabledAt == null &&
                                    !user.emailVerified &&
                                    userMatches(user.email, email)
                            }
                            ?.let { PendingRegistration(userId, challenge.binding) }
                    }
                }

        private fun userMatches(accountEmail: Email, requestedEmail: Email): Boolean =
            accountEmail.value.equals(requestedEmail.value, ignoreCase = true)

        private suspend fun markVerified(userId: UserId): Boolean =
            transactions.inTransaction { Verdict.Commit(users.markEmailVerified(userId)) }

        private data class PendingRegistration(val userId: UserId, val binding: String)
    }
}
