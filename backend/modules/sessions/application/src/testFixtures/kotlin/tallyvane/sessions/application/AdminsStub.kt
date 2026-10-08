package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins

/**
 * [Admins] that has been told who holds the right, and can be told it was taken away.
 */
class AdminsStub : Admins {
    private val holders = mutableSetOf<AccountId>()

    fun grant(account: AccountId) {
        holders += account
    }

    fun revoke(account: AccountId) {
        holders -= account
    }

    override fun isAdmin(account: AccountId): Boolean = account in holders

    override fun toString(): String = "AdminsStub(${holders.size})"
}
