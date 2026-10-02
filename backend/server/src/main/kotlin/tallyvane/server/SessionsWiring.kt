package tallyvane.server

import tallyvane.authentication.contract.SignIns
import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.server.config.SignInConfiguration
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.OpenSessionUseCase
import tallyvane.sessions.application.SessionKeys
import tallyvane.sessions.application.SignOutUseCase
import tallyvane.sessions.domain.Lifetimes
import tallyvane.sessions.infrastructure.SessionsStorageFactory
import tallyvane.sessions.web.SessionsWebFactory

/**
 * `sessions`: what a person is given once they have proved who they are, and how every later request is
 * recognised by it.
 *
 * The pepper is the one the sign-in uses: it is the key under which a browser's secret becomes what the
 * database keeps, and what each table keeps is told apart by the table, not by the key.
 *
 * Everything is deferred, so construction touches no database.
 */
public class SessionsWiring(
    private val platform: PlatformWiring,
    private val signIns: SignIns,
    settings: SignInConfiguration,
) {
    private val storage = SessionsStorageFactory(platform.ids)

    private val clock: Clock = Clock.Wall()

    private val lifetimes = Lifetimes.forBrowser()

    private val keys = SessionKeys(SecretGenerator.Csprng(), Digests.Hmac(settings.tokenPepper, settings.pepperVersion))

    private val web = SessionsWebFactory()

    private val open: OpenSessionUseCase by lazy {
        OpenSessionUseCase.OpenSession(
            signIns,
            storage.sessions(),
            platform.persistence.transactions,
            clock,
            keys,
            lifetimes,
        )
    }

    private val signOut: SignOutUseCase by lazy {
        SignOutUseCase.SignOut(storage.sessions(), platform.persistence.transactions, keys)
    }

    private val authenticate: AuthenticateUseCase by lazy {
        AuthenticateUseCase.Authenticate(storage.sessions(), platform.persistence.transactions, clock, keys, lifetimes)
    }

    /**
     * How every request is recognised.
     */
    public val callers: Callers by lazy { web.callers(authenticate) }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy { listOf(web.open(open), web.signOut(signOut)) }

    override fun toString(): String = "SessionsWiring"
}
