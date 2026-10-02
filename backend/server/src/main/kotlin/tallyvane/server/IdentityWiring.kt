package tallyvane.server

import tallyvane.identity.application.AccountDirectory
import tallyvane.identity.contract.Accounts
import tallyvane.identity.infrastructure.IdentityStorageFactory

/**
 * `identity`, as its neighbours see it: the accounts it answers for.
 *
 * Nothing here touches a database; the adapter runs inside the transaction of whoever asks.
 */
public class IdentityWiring(private val platform: PlatformWiring) {
    public val accounts: Accounts by lazy {
        AccountDirectory(IdentityStorageFactory().accounts(), platform.ids)
    }

    override fun toString(): String = "IdentityWiring"
}
