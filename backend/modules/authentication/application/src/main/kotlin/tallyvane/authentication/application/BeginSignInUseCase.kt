package tallyvane.authentication.application

import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Surface
import tallyvane.platform.kernel.UseCase

/**
 * A person presses "Sign in with Google".
 *
 * Every sign-in starts as a login (slice 3, fork 3): nobody can tell a new person from a returning
 * one until Google has said who they are. What it is a login *for* is the door they came through
 * (ADR-097): the console's `login`, or the administrators' `admin_login`, each with a policy of its own.
 */
public interface BeginSignInUseCase : UseCase {
    public suspend fun begin(surface: Surface): SignInBegun

    public class BeginSignIn(private val departures: Departures) : BeginSignInUseCase {
        override suspend fun begin(surface: Surface): SignInBegun = departures.begin(purposeOn(surface), surface)

        private fun purposeOn(surface: Surface): Purpose = when (surface) {
            Surface.App -> Purpose.Login
            Surface.Admin -> Purpose.AdminLogin
        }
    }
}
