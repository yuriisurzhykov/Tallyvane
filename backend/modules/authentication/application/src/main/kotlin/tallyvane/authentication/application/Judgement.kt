package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import kotlin.time.Instant

/**
 * Decides, at [now], whether a return from Google may be traded with Google.
 *
 * Asked inside the transaction that took the handshake, so the policy it reads and the attempt it
 * judges are the ones of that moment.
 */
internal class Judgement(private val policies: ActivePolicies, private val now: Instant) {
    /**
     * Where [trip] goes with [reply]: on to Google with the code, or back to the sign-in page.
     */
    fun of(trip: Trip, reply: GoogleReply): Arrival {
        val purpose = THROUGH_GOOGLE.firstOrNull(trip.attempt::isFor)
        return when {
            reply !is GoogleReply.Granted -> Arrival.Stopped(TurnBack.Cancelled)
            !trip.answers(reply.state) -> Arrival.Stopped(TurnBack.Restart)
            purpose == null -> Arrival.Stopped(TurnBack.Restart)
            else -> policies.progressOf(trip.attempt, purpose, now).reportTo(GoogleAwaited(trip, reply.code))
        }
    }

    private companion object {
        /**
         * The purposes that begin with a trip to Google and come back to this callback: signing in, and
         * confirming a dangerous act. Registration is never begun; a sign-in becomes one on the way back.
         */
        val THROUGH_GOOGLE = listOf(Purpose.Login, Purpose.StepUp)
    }

    /**
     * Lets the trip on only while the policy is waiting for Google.
     */
    private class GoogleAwaited(private val trip: Trip, private val code: String) : Progress.Report<Arrival> {
        override fun awaiting(accepted: Set<FactorKind>): Arrival =
            if (FactorKind.Google in accepted) Arrival.Ready(trip, code) else Arrival.Stopped(TurnBack.Restart)

        override fun expired(): Arrival = Arrival.Stopped(TurnBack.Expired)

        override fun paused(accepted: Set<FactorKind>, until: Instant): Arrival = Arrival.Stopped(TurnBack.Restart)

        override fun exhausted(): Arrival = Arrival.Stopped(TurnBack.Restart)

        override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): Arrival =
            Arrival.Stopped(TurnBack.Restart)

        override fun restricted(
            factors: Set<FactorKind>,
            authenticatedAt: Instant,
            subject: String,
            toSetUp: Set<FactorKind>,
        ): Arrival = Arrival.Stopped(TurnBack.Restart)
    }
}
