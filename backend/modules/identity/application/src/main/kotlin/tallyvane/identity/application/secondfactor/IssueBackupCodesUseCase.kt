package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface IssueBackupCodesUseCase : UseCase {
    /**
     * Replaces the previous set after the caller re-proves their current password.
     */
    public suspend fun issue(userId: UserId, currentPassword: Secret): List<Secret>?

    public class Issue(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val codes: BackupCodes,
        private val transactions: TransactionRunner,
    ) : IssueBackupCodesUseCase {
        override suspend fun issue(userId: UserId, currentPassword: Secret): List<Secret>? =
            transactions.inTransaction {
                val user = users.findById(userId)
                val credential = credentials.findPasswordFor(userId)
                val verified = user != null &&
                    user.disabledAt == null &&
                    user.emailVerified &&
                    credential != null &&
                    passwords.verify(currentPassword, credential.hash)
                if (verified) {
                    Verdict.Commit(codes.issue(userId))
                } else {
                    Verdict.Rollback<List<Secret>?>(null)
                }
            }
    }
}
