package tallyvane.identity.application.account

import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * Cohesive account settings operations sharing the same repository and transaction boundary.
 */
public class AccountSettings internal constructor(
    private val users: UserRepository,
    private val transactions: TransactionRunner,
) {
    public suspend fun read(userId: UserId): User? = transactions.inTransaction {
        Verdict.Commit(users.findById(userId))
    }

    public suspend fun updateDisplayName(userId: UserId, displayName: String?): User? = transactions.inTransaction {
        if (!users.updateDisplayName(userId, displayName)) return@inTransaction Verdict.Commit(null)
        Verdict.Commit(users.findById(userId))
    }

    public suspend fun updateSecurityEmails(userId: UserId, enabled: Boolean): User? = transactions.inTransaction {
        if (!users.updateSecurityEmails(userId, enabled)) return@inTransaction Verdict.Commit(null)
        Verdict.Commit(users.findById(userId))
    }
}
