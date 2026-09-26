package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface IssueBackupCodesUseCase : UseCase {
    public suspend fun issue(userId: UserId, sessionId: SessionId, actionProof: String?): List<Secret>?

    public class Issue(
        private val codes: BackupCodes,
        private val transactions: TransactionRunner,
        private val actionProofs: AuthenticationActionProofRequirement?,
    ) : IssueBackupCodesUseCase {
        override suspend fun issue(userId: UserId, sessionId: SessionId, actionProof: String?): List<Secret>? =
            transactions.inTransaction {
                val authorized = actionProofs?.consume(
                    actionProof, userId, sessionId, AuthenticationAction.MANAGE_SECOND_FACTORS,
                ) == true
                if (authorized) {
                    Verdict.Commit(codes.issue(userId))
                } else {
                    Verdict.Rollback<List<Secret>?>(null)
                }
            }
    }
}
