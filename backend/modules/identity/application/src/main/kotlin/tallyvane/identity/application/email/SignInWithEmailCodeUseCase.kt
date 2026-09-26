package tallyvane.identity.application.email

import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Completes a primary sign-in using an email challenge issued only for the EMAIL_LOGIN purpose.
 */
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
            return if (belongsToLogin(challenge, request.email) && verifies(request)) {
                completeSignIn(request)
            } else {
                invalidCredential()
            }
        }

        private fun belongsToLogin(
            challenge: tallyvane.identity.domain.email.EmailChallenge?,
            email: tallyvane.identity.domain.user.Email,
        ): Boolean = challenge?.let {
            it.purpose == EmailChallengePurpose.EMAIL_LOGIN &&
                it.email.value.equals(email.value, ignoreCase = true) &&
                it.binding.isEmpty()
        } == true

        private suspend fun verifies(request: SignInWithEmailCodeRequest): Boolean = challenges.verify(
            request.challengeId,
            request.email,
            EmailChallengePurpose.EMAIL_LOGIN,
            request.code,
        )

        private suspend fun completeSignIn(request: SignInWithEmailCodeRequest): SignInOutcome =
            transactions.inTransaction {
                val user = users.findByEmail(request.email)
                val result = when {
                    user == null || !user.emailVerified -> SignInOutcome.NotIssued(
                        AuthenticationOutcome.InvalidCredential,
                    )
                    user.disabledAt != null -> SignInOutcome.NotIssued(AuthenticationOutcome.AccountDisabled)
                    else -> completer.complete(user.id, request.device, PrimaryMethod.EMAIL_CODE)
                }
                Verdict.Commit(result)
            }

        private fun invalidCredential() = SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
    }
}
