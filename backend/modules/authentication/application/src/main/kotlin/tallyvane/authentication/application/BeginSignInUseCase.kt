package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.UseCase

/**
 * A person presses "Sign in with Google".
 *
 * Every sign-in starts as a login (slice 3, fork 3): nobody can tell a new person from a returning
 * one until Google has said who they are.
 */
public interface BeginSignInUseCase : UseCase {
    public suspend fun begin(): SignInBegun

    public class BeginSignIn(
        private val trips: GoogleTrips,
        private val google: Google,
        private val clock: Clock,
        private val keys: SignInKeys,
    ) : BeginSignInUseCase {
        override suspend fun begin(): SignInBegun {
            val issued = keys.issue()
            val handshake = keys.handshake()
            trips.depart(issued.key, Attempt(Purpose.Login, clock.now()), handshake)
            return SignInBegun(issued.secret, google.addressFor(handshake))
        }
    }
}
