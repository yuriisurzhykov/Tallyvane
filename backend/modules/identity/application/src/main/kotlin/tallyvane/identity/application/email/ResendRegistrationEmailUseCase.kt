package tallyvane.identity.application.email

import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/** Reissues verification only for the matching, still-unverified account. */
public interface ResendRegistrationEmailUseCase : UseCase {
    public suspend fun resend(userId: UserId, email: Email): Uuid?

    public class Send(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
    ) : ResendRegistrationEmailUseCase {
        override suspend fun resend(userId: UserId, email: Email): Uuid? {
            val eligible = transactions.inTransaction {
                val user = users.findById(userId)
                Verdict.Commit(user != null && !user.emailVerified && user.disabledAt == null &&
                    user.email.value.equals(email.value, ignoreCase = true))
            }
            if (!eligible) return null
            return challenges.issue(email, EmailChallengePurpose.REGISTRATION, userId.value.toString())?.id
        }
    }
}
