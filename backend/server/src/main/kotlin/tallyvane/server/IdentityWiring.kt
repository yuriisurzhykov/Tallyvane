package tallyvane.server

import tallyvane.identity.application.AccountDirectory
import tallyvane.identity.application.WhoAmIUseCase
import tallyvane.identity.contract.Accounts
import tallyvane.identity.infrastructure.IdentityStorageFactory
import tallyvane.identity.web.IdentityRoutesFactory
import tallyvane.platform.http.RouteModule

/**
 * `identity`, as its neighbours see it: the accounts it answers for, and the routes through which a
 * signed-in person asks who they are.
 *
 * Nothing here touches a database; the adapter runs inside the transaction of whoever asks.
 */
public class IdentityWiring(private val platform: PlatformWiring) {
    public val accounts: Accounts by lazy {
        AccountDirectory(IdentityStorageFactory().accounts(), platform.ids)
    }

    private val whoAmI: WhoAmIUseCase by lazy {
        WhoAmIUseCase.WhoAmI(IdentityStorageFactory().accounts(), platform.persistence.transactions)
    }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy { listOf(IdentityRoutesFactory().me(whoAmI)) }

    override fun toString(): String = "IdentityWiring"
}
