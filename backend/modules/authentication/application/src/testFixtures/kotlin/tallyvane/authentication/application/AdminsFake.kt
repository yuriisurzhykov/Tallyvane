package tallyvane.authentication.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins

/**
 * [Admins] that has been told who holds the right. Nobody does until a spec says so.
 */
class AdminsFake : Admins {
    private val holders = mutableSetOf<AccountId>()

    fun grant(account: AccountId) {
        holders += account
    }

    override fun isAdmin(account: AccountId): Boolean = account in holders

    override fun toString(): String = "AdminsFake(${holders.size})"
}
