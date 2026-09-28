package tallyvane.identity.application.email

import tallyvane.identity.application.login.ReadSignInOptionsUseCase
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.UseCase

/**
 * Issues an email sign-in code with the EMAIL_LOGIN purpose and the shared resend policy.
 */
public interface RequestEmailSignInCodeUseCase : UseCase {
    public suspend fun request(email: Email): Result

    public sealed interface Result {
        public data class Issued(val challenge: EmailChallenge) : Result
        public data object Refused : Result
        public data object RateLimited : Result
    }

    public class Issue internal constructor(
        private val challenges: EmailChallenges,
        private val signInOptions: ReadSignInOptionsUseCase,
    ) : RequestEmailSignInCodeUseCase {
        override suspend fun request(email: Email): Result =
            if (AuthenticationTokenKind.EMAIL_SIGN_IN_CODE !in signInOptions.read()) {
                Result.Refused
            } else {
                challenges.issue(email, EmailChallengePurpose.EMAIL_LOGIN)?.let(Result::Issued)
                    ?: Result.RateLimited
            }
    }
}
