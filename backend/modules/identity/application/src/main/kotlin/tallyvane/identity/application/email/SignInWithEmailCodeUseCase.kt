package tallyvane.identity.application.email

import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/** Completes a primary sign-in using an email challenge issued only for the EMAIL_LOGIN purpose. */
public interface SignInWithEmailCodeUseCase : UseCase {
    public suspend fun signIn(request: SignInWithEmailCodeRequest): SignInOutcome

    public class SignIn internal constructor(
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val completer: AuthenticationCompleter,
        private val transactions: TransactionRunner,
    ) : SignInWithEmailCodeUseCase {
        override suspend fun signIn(request: SignInWithEmailCodeRequest): SignInOutcome {
            val challenge = challenges.challenge(request.challengeId)
                ?: return SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            if (challenge.purpose != EmailChallengePurpose.EMAIL_LOGIN ||
                !challenge.email.value.equals(request.email.value, ignoreCase = true) || challenge.binding.isNotEmpty()
            ) return SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)

            if (!challenges.verify(request.challengeId, request.email, EmailChallengePurpose.EMAIL_LOGIN, request.code)) {
                return SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            }

            return transactions.inTransaction {
                val user = users.findByEmail(request.email)
                val result = when {
                    user == null || !user.emailVerified -> SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
                    user.disabledAt != null -> SignInOutcome.NotIssued(AuthenticationOutcome.AccountDisabled)
                    else -> completer.complete(user.id, request.device)
                }
                Verdict.Commit(result)
            }
        }
    }
}
