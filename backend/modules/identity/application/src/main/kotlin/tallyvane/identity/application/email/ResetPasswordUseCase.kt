package tallyvane.identity.application.email

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordPolicy
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface ResetPasswordUseCase : UseCase {
    /** Returns false for an invalid code. A valid code always has an account-neutral outcome. */
    public suspend fun reset(request: ResetPasswordRequest): Boolean

    public class Replace(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwordHasher: PasswordHasher,
        private val challenges: EmailChallenges,
        private val transactions: TransactionRunner,
        private val passwordPolicy: PasswordPolicy = PasswordPolicy.Default,
    ) : ResetPasswordUseCase {
        override suspend fun reset(request: ResetPasswordRequest): Boolean {
            val rawPassword = request.newPassword.revealed()
            if (!passwordPolicy.accepts(rawPassword)) return false
            val challenge = challenges.challenge(request.challengeId) ?: return false
            if (challenge.purpose != EmailChallengePurpose.PASSWORD_RESET ||
                !challenge.email.value.equals(request.email.value, ignoreCase = true)
            ) return false
            if (!challenges.verify(request.challengeId, request.email, EmailChallengePurpose.PASSWORD_RESET, request.code)) return false

            val user = transactions.inTransaction { Verdict.Commit(users.findByEmail(request.email)) } ?: return true
            if (!user.emailVerified || user.disabledAt != null) return true
            val credential = Credential.PasswordRecord(passwordHasher.hash(request.newPassword))
            transactions.inTransaction {
                Verdict.Commit(credentials.saveOrReplacePasswordFor(user.id, credential))
            }
            return true
        }
    }
}
