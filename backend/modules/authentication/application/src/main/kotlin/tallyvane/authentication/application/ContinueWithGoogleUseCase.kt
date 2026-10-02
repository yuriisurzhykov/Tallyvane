package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.UseCase

/**
 * A person comes back from Google.
 *
 * The handshake is taken first and committed, then the code is traded with Google outside any
 * transaction, then what Google said is recorded. A person Google identified who has an account goes
 * on with their sign-in; one who has none goes to the welcome screen with a registration of their own.
 */
public interface ContinueWithGoogleUseCase : UseCase {
    /**
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     */
    public suspend fun continueWith(attempt: Secret?, reply: GoogleReply): GoogleReturn

    public class ContinueWithGoogle(
        private val trips: GoogleTrips,
        private val google: Google,
        private val policies: ActivePolicies,
        private val clock: Clock,
        private val keys: SignInKeys,
    ) : ContinueWithGoogleUseCase {
        override suspend fun continueWith(attempt: Secret?, reply: GoogleReply): GoogleReturn {
            val key = attempt?.let(keys::keyOf) ?: return GoogleReturn.TurnedBack(TurnBack.Restart)
            return when (val arrival = trips.arrive(key, reply, Judgement(policies, clock.now()))) {
                is Arrival.Stopped -> GoogleReturn.TurnedBack(arrival.reason)
                is Arrival.Ready -> arrival.trip.exchangeWith(google, arrival.code).reportTo(Heard(arrival.trip))()
            }
        }

        /**
         * What to do with each answer Google can give, as the step that does it.
         */
        private inner class Heard(private val trip: Trip) : GoogleAnswer.Report<suspend () -> GoogleReturn> {
            override fun vouched(subject: String, profile: GoogleProfile): suspend () -> GoogleReturn = {
                trips.land(trip, Identified(subject, profile, clock.now()), keys.issue())
            }

            override fun emailUnverified(): suspend () -> GoogleReturn = {
                trips.abandon(trip, TurnBack.EmailUnverified)
            }

            override fun refused(): suspend () -> GoogleReturn = { trips.abandon(trip, TurnBack.Refused) }

            override fun unreachable(): suspend () -> GoogleReturn = { trips.abandon(trip, TurnBack.Unavailable) }
        }
    }
}
