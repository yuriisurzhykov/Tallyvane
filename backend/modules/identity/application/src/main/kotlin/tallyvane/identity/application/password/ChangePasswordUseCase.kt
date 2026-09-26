package tallyvane.identity.application.password

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Changes a signed-in user's password only after proving the current credential again.
 */
public interface ChangePasswordUseCase : UseCase {
    public suspend fun change(userId: UserId, sessionId: SessionId, actionProof: String?, newPassword: String): Boolean

    public class Change(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
        private val transactions: TransactionRunner,
        private val actionProofs: tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement?,
        private val policy: PasswordPolicy = PasswordPolicy.Default,
    ) : ChangePasswordUseCase {
        override suspend fun change(
            userId: UserId,
            sessionId: SessionId,
            actionProof: String?,
            newPassword: String,
        ): Boolean {
            if (!policy.accepts(newPassword)) return false
            return transactions.inTransaction {
                val user = users.findById(userId)
                val record = credentials.findPasswordFor(userId)
                val accountCanChangePassword = user != null && user.disabledAt == null && user.emailVerified
                val authorized = actionProofs?.consume(
                    actionProof, userId, sessionId, AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                ) == true
                if (!accountCanChangePassword || !authorized) {
                    Verdict.Rollback(false)
                } else if (record != null && passwords.verify(tallyvane.platform.kernel.Secret(newPassword), record.hash)
                ) {
                    Verdict.Rollback(false)
                } else {
                    credentials.saveOrReplacePasswordFor(
                        userId,
                        Credential.PasswordRecord(passwords.hash(tallyvane.platform.kernel.Secret(newPassword))),
                    )
                    Verdict.Commit(true)
                }
            }
        }
    }
}
