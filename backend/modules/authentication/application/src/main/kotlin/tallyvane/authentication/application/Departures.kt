package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock

/**
 * Sends a person to Google for an attempt of some purpose: the one thing signing in and confirming a
 * dangerous act both begin with. What differs between them is the purpose, and nothing else.
 */
internal class Departures(
    private val trips: GoogleTrips,
    private val google: Google,
    private val clock: Clock,
    private val keys: SignInKeys,
) {
    /**
     * Keeps a new attempt for [purpose], and says where to send the person.
     */
    suspend fun begin(purpose: Purpose): SignInBegun {
        val issued = keys.issue()
        val handshake = keys.handshake()
        trips.depart(issued.key, Attempt(purpose, clock.now()), handshake)
        return SignInBegun(issued.secret, google.addressFor(handshake))
    }

    override fun toString(): String = "Departures($trips)"
}
