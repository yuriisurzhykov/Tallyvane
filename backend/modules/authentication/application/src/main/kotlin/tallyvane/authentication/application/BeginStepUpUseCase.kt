package tallyvane.authentication.application

import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Surface
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
    public suspend fun begin(surface: Surface): SignInBegun

    public class BeginStepUp(private val departures: Departures) : BeginStepUpUseCase {
        override suspend fun begin(surface: Surface): SignInBegun = departures.begin(Purpose.StepUp, surface)
    }
}
