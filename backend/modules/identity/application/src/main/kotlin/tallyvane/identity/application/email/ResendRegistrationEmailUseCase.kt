package tallyvane.identity.application.email

import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * Reissues verification only for the matching, still-unverified account.
 */
public interface ResendRegistrationEmailUseCase : UseCase {
    public suspend fun resend(email: Email): Uuid?

    public class Send(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
    ) : ResendRegistrationEmailUseCase {
        override suspend fun resend(email: Email): Uuid? {
            val user = transactions.inTransaction { Verdict.Commit(users.findByEmail(email)) } ?: return null
            if (
                user.emailVerified || user.disabledAt != null ||
                !user.email.value.equals(email.value, ignoreCase = true)
            ) return null
            return challenges.issue(email, EmailChallengePurpose.REGISTRATION, user.id.value.toString())?.id
        }
    }
}
