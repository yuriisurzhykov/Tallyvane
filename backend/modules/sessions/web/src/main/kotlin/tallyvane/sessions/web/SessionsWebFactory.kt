package tallyvane.sessions.web

import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.ConfirmStepUpUseCase
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
    public fun open(open: OpenSessionUseCase, surfaces: Surfaces): RouteModule =
        SessionRoutes(open, SessionCookie(), SpentAttemptCookie(), SessionProblems(), surfaces)

    /**
     * `POST /step-ups`: proving who the person is again, for a dangerous act.
     */
    public fun stepUp(confirm: ConfirmStepUpUseCase, surfaces: Surfaces): RouteModule =
        ConfirmationRoutes(confirm, SessionCookie(), SpentAttemptCookie(), ConfirmationProblems(), surfaces)

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
     * How every request is recognised: by its session cookie, if that session is one of the door the request
     * came through (ADR-097).
     */
    public fun callers(authenticate: AuthenticateUseCase, surfaces: Surfaces): Callers =
        SessionCallers(authenticate, SessionCookie(), surfaces)

    override fun toString(): String = "SessionsWebFactory"
}
