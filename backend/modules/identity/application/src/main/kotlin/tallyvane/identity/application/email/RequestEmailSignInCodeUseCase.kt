package tallyvane.identity.application.email

import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.UseCase

/**
 * Issues an email sign-in code with the EMAIL_LOGIN purpose and the shared resend policy.
 */
public interface RequestEmailSignInCodeUseCase : UseCase {
    public suspend fun request(email: Email): EmailChallenge?

    public class Issue internal constructor(private val challenges: EmailChallenges) : RequestEmailSignInCodeUseCase {
        override suspend fun request(email: Email): EmailChallenge? =
            challenges.issue(email, EmailChallengePurpose.EMAIL_LOGIN)
    }
}
