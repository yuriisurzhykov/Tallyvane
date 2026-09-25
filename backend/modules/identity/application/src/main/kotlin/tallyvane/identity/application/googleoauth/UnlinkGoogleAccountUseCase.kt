package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface UnlinkGoogleAccountUseCase : UseCase {
    public suspend fun unlink(userId: UserId, password: Secret): Result

    public enum class Result {
        Unlinked,
        InvalidCredential,
        LastSignInMethod,
        NotLinked,
    }

    public class Unlink(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val transactions: TransactionRunner,
    ) : UnlinkGoogleAccountUseCase {
        override suspend fun unlink(userId: UserId, password: Secret): Result = transactions.inTransaction {
            val user = users.findById(userId)
            val passwordRecord = credentials.findPasswordFor(userId)
            val googleRecord = credentials.findGoogleFor(userId)
            when {
                user == null || user.disabledAt != null || !user.emailVerified ->
                    Verdict.Rollback(Result.InvalidCredential)
                passwordRecord == null -> Verdict.Rollback(Result.LastSignInMethod)
                !passwords.verify(password, passwordRecord.hash) ->
                    Verdict.Rollback(Result.InvalidCredential)
                googleRecord == null -> Verdict.Rollback(Result.NotLinked)
                credentials.deleteGoogleFor(userId) -> Verdict.Commit(Result.Unlinked)
                else -> Verdict.Rollback(Result.NotLinked)
            }
        }
    }
}
