package tallyvane.server

import tallyvane.authentication.contract.SignIns
import tallyvane.identity.contract.Admins
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.events.EventSubscriber
import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.server.config.SignInConfiguration
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.ConfirmStepUpUseCase
import tallyvane.sessions.application.DeviceWords
import tallyvane.sessions.application.ListDevicesUseCase
import tallyvane.sessions.application.OpenSessionUseCase
import tallyvane.sessions.application.Recognition
import tallyvane.sessions.application.RenameDeviceUseCase
import tallyvane.sessions.application.RevokeDeviceUseCase
import tallyvane.sessions.application.SessionKeys
import tallyvane.sessions.application.SessionsOfDeletedAccounts
import tallyvane.sessions.application.SignOutOthersUseCase
import tallyvane.sessions.application.SignOutUseCase
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
    private val admins: Admins,
    private val journal: SecurityJournal,
    settings: SignInConfiguration,
    private val surfaces: Surfaces,
) {
    private val storage = SessionsStorageFactory()

    private val clock: Clock = Clock.Wall()

    private val keys = SessionKeys(
        SecretGenerator.Csprng(),
        Digests.Hmac(settings.tokenPepper, settings.pepperVersion),
        platform.ids,
    )

    private val recognition by lazy {
        Recognition(storage.sessions(), storage.lifetimeVersions(), clock, keys)
    }

    private val web = SessionsWebFactory()

    private val open: OpenSessionUseCase by lazy {
        OpenSessionUseCase.OpenSession(
            signIns,
            admins,
            storage.sessions(),
            journal,
            DeviceWords(),
            platform.persistence.transactions,
            clock,
            keys,
        )
    }

    private val confirmStepUp: ConfirmStepUpUseCase by lazy {
        ConfirmStepUpUseCase.ConfirmStepUp(
            recognition,
            signIns,
            storage.sessions(),
            platform.persistence.transactions,
            keys,
        )
    }

    private val signOut: SignOutUseCase by lazy {
        SignOutUseCase.SignOut(storage.sessions(), platform.persistence.transactions, keys)
    }

    private val authenticate: AuthenticateUseCase by lazy {
        AuthenticateUseCase.Authenticate(recognition, platform.persistence.transactions)
    }

    private val listDevices: ListDevicesUseCase by lazy {
        ListDevicesUseCase.ListDevices(
            recognition,
            storage.sessions(),
            storage.lifetimeVersions(),
            platform.persistence.transactions,
            clock,
        )
    }

    private val revokeDevice: RevokeDeviceUseCase by lazy {
        RevokeDeviceUseCase.RevokeDevice(recognition, storage.sessions(), platform.persistence.transactions)
    }

    private val renameDevice: RenameDeviceUseCase by lazy {
        RenameDeviceUseCase.RenameDevice(recognition, storage.sessions(), platform.persistence.transactions)
    }

    private val signOutOthers: SignOutOthersUseCase by lazy {
        SignOutOthersUseCase.SignOutOthers(recognition, storage.sessions(), journal, platform.persistence.transactions)
    }

    /**
     * How every request is recognised.
     */
    public val callers: Callers by lazy { web.callers(authenticate, surfaces) }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy {
        listOf(
            web.open(open, surfaces),
            web.stepUp(confirmStepUp, surfaces),
            web.signOut(signOut),
            web.devices(listDevices),
            web.deviceSignOut(revokeDevice),
            web.deviceName(renameDevice),
            web.otherDevicesSignOut(signOutOthers),
        )
    }

    /**
     * What this module answers when other modules say something happened: an account being deleted ends
     * every session it had. The subscribers run in the transaction of whoever publishes (ADR-090).
     */
    public val subscribers: List<EventSubscriber<*>> by lazy { listOf(SessionsOfDeletedAccounts(storage.sessions())) }

    override fun toString(): String = "SessionsWiring"
}
