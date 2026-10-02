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
    fun of(trip: Trip, reply: GoogleReply): Arrival = when {
        reply !is GoogleReply.Granted -> Arrival.Stopped(TurnBack.Cancelled)
        !trip.answers(reply.state) -> Arrival.Stopped(TurnBack.Restart)
        !trip.attempt.isFor(Purpose.Login) -> Arrival.Stopped(TurnBack.Restart)
        else -> policies.progressOf(trip.attempt, Purpose.Login, now).reportTo(GoogleAwaited(trip, reply.code))
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
