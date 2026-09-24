package tallyvane.identity.application.email

import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

public class BackupCodes(
    private val store: BackupCodeStore,
    private val codes: AuthenticationCodes,
    private val transactions: TransactionRunner,
) {
    public suspend fun issue(userId: UserId): List<Secret> {
        val issued = List(10) { codes.backupCode() }
        transactions.inTransaction {
            store.replace(userId, issued.map { codes.hash("backup:${userId.value}", it) })
            Verdict.Commit(Unit)
        }
        return issued
    }

    public suspend fun consume(userId: UserId, code: Secret): Boolean = transactions.inTransaction {
        Verdict.Commit(store.consume(userId, codes.hash("backup:${userId.value}", code)))
    }
}
