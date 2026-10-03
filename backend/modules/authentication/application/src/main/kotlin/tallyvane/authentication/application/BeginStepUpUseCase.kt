package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.UseCase

/**
 * A person who is signed in is asked to prove who they are again, before a dangerous act (ADR-092).
 *
 * It begins like a sign-in, with Google, and for the same reason: whoever confirms must show the very
 * thing sign-in asks for, and never less (ADR-078). It does not know whose session asks, because
 * `authentication` does not know sessions exist; the module that grants sessions compares the account
 * this confirmation proves with the session's own when it takes the confirmation.
 */
public interface BeginStepUpUseCase : UseCase {
    public suspend fun begin(): SignInBegun

    public class BeginStepUp(trips: GoogleTrips, google: Google, clock: Clock, keys: SignInKeys) : BeginStepUpUseCase {
        private val departures = Departures(trips, google, clock, keys)

        override suspend fun begin(): SignInBegun = departures.begin(Purpose.StepUp)
    }
}
