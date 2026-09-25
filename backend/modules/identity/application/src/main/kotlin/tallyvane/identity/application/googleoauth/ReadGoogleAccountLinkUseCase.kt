package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface ReadGoogleAccountLinkUseCase : UseCase {
    public suspend fun isLinked(userId: UserId): Boolean

    public class Read(
        private val credentials: CredentialRepository,
        private val transactions: TransactionRunner,
    ) : ReadGoogleAccountLinkUseCase {
        override suspend fun isLinked(userId: UserId): Boolean = transactions.inTransaction {
            Verdict.Commit(credentials.findGoogleFor(userId) != null)
        }
    }
}
