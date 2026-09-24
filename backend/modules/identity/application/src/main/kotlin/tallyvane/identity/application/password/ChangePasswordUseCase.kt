package tallyvane.identity.application.password

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordPolicy
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/** Changes a signed-in user's password only after proving the current credential again. */
public interface ChangePasswordUseCase : UseCase {
    public suspend fun change(userId: UserId, currentPassword: Secret, newPassword: Secret): Boolean

    public class Change(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val transactions: TransactionRunner,
        private val policy: PasswordPolicy = PasswordPolicy.Default,
    ) : ChangePasswordUseCase {
        override suspend fun change(userId: UserId, currentPassword: Secret, newPassword: Secret): Boolean {
            if (!policy.accepts(newPassword.revealed())) return false
            return transactions.inTransaction {
                val user = users.findById(userId)
                val record = credentials.findPasswordFor(userId)
                if (user == null || user.disabledAt != null || !user.emailVerified || record == null) {
                    Verdict.Rollback(false)
                } else if (!passwords.verify(currentPassword, record.hash) || passwords.verify(newPassword, record.hash)) {
                    Verdict.Rollback(false)
                } else {
                    credentials.saveOrReplacePasswordFor(userId, Credential.PasswordRecord(passwords.hash(newPassword)))
                    Verdict.Commit(true)
                }
            }
        }
    }
}
