package tallyvane.authentication.application

import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.UseCase

/**
 * A person presses "Sign in with Google".
 *
 * Every sign-in starts as a login (slice 3, fork 3): nobody can tell a new person from a returning
 * one until Google has said who they are.
 */
public interface BeginSignInUseCase : UseCase {
    public suspend fun begin(): SignInBegun

    public class BeginSignIn(private val departures: Departures) : BeginSignInUseCase {
        override suspend fun begin(): SignInBegun = departures.begin(Purpose.Login)
    }
}
