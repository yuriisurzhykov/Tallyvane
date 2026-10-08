package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Surface

/**
 * Sends a person to Google for an attempt of some purpose: the one thing signing in and confirming a
 * dangerous act both begin with. What differs between them is the purpose, and nothing else, so it is
 * built once and handed to both use cases.
 */
public class Departures(
    private val trips: GoogleTrips,
    private val google: Google,
    private val clock: Clock,
    private val keys: SignInKeys,
) {
    /**
     * Keeps a new attempt for [purpose], and says where to send a person who is on [surface].
     */
    public suspend fun begin(purpose: Purpose, surface: Surface): SignInBegun {
        val issued = keys.issue()
        val handshake = keys.handshake()
        trips.depart(issued.key, Attempt(purpose, clock.now()), handshake)
        return SignInBegun(issued.secret, google.addressFor(handshake, surface))
    }

    override fun toString(): String = "Departures($trips)"
}
