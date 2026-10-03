package tallyvane.sessions.web

import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.ListDevicesUseCase
import tallyvane.sessions.application.OpenSessionUseCase
import tallyvane.sessions.application.RenameDeviceUseCase
import tallyvane.sessions.application.RevokeDeviceUseCase
import tallyvane.sessions.application.SignOutOthersUseCase
import tallyvane.sessions.application.SignOutUseCase

/**
 * Hands out what this module serves, for the composition root to mount. The route classes are
 * `internal`; one takes one use case (`web-one-usecase`), so this is where the root, which holds all of
 * them, asks for each.
 */
public class SessionsWebFactory {
    /**
     * `POST /sessions`: exchanging a completed sign-in for a session.
     */
    public fun open(open: OpenSessionUseCase): RouteModule =
        SessionRoutes(open, SessionCookie(), SpentAttemptCookie(), SessionProblems())

    /**
     * `DELETE /session`: signing out.
     */
    public fun signOut(signOut: SignOutUseCase): RouteModule = SignOutRoutes(signOut, SessionCookie())

    /**
     * `GET /devices`: the devices the person is signed in on.
     */
    public fun devices(list: ListDevicesUseCase): RouteModule =
        DeviceListRoutes(list, SessionCookie(), DeviceProblems())

    /**
     * `DELETE /device/{id}`: signing out on one device.
     */
    public fun deviceSignOut(revoke: RevokeDeviceUseCase): RouteModule =
        DeviceSignOutRoutes(revoke, SessionCookie(), DeviceProblems())

    /**
     * `PUT /device-names/{id}`: naming a device.
     */
    public fun deviceName(rename: RenameDeviceUseCase): RouteModule =
        DeviceNameRoutes(rename, SessionCookie(), DeviceProblems())

    /**
     * `DELETE /other-devices`: signing out everywhere but here.
     */
    public fun otherDevicesSignOut(signOutOthers: SignOutOthersUseCase): RouteModule =
        OtherDevicesSignOutRoutes(signOutOthers, SessionCookie(), DeviceProblems())

    /**
     * How every request is recognised: by its session cookie.
     */
    public fun callers(authenticate: AuthenticateUseCase): Callers = SessionCallers(authenticate, SessionCookie())

    override fun toString(): String = "SessionsWebFactory"
}
