package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleHandshakes
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.authentication.domain.Attempt
import tallyvane.identity.contract.Accounts
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * Everything kept about a sign-in that goes through Google, changed one transaction at a time.
 *
 * Each method is one transaction of its own, and none is called inside another. A return from Google
 * is spread over two, on purpose: the handshake is taken and committed before Google is asked, so a
 * reply that arrives twice finds nothing the second time, and no transaction is held open while
 * waiting on Google's network.
 */
public class GoogleTrips(
    private val attempts: Attempts,
    private val handshakes: GoogleHandshakes,
    private val profiles: GoogleProfiles,
    private val accounts: Accounts,
    private val transactions: TransactionRunner,
) {
    /**
     * Keeps a new [attempt] under [key], with the [handshake] it is about to send to Google.
     */
    internal suspend fun depart(key: Digest, attempt: Attempt, handshake: GoogleHandshake) {
        transactions.inTransaction {
            check(attempts.save(key, attempt) == AttemptSaveOutcome.Saved) {
                "A new attempt found another already kept under its key. Two 256-bit secrets do not " +
                    "collide; look for a SecretGenerator that repeats itself."
            }
            handshakes.keep(key, handshake)
            Verdict.Commit(Unit)
        }
    }

    /**
     * Takes the handshake kept under [key] and judges, with [judgement], whether [reply] may go on to
     * Google. A trip that may not is forgotten in the same transaction.
     */
    internal suspend fun arrive(key: Digest, reply: GoogleReply, judgement: Judgement): Arrival =
        transactions.inTransaction {
            val handshake = handshakes.take(key)
            val trip = handshake?.let { taken -> attempts.find(key)?.let { Trip(key, it, taken) } }
            val arrival = trip?.let { judgement.of(it, reply) } ?: Arrival.Stopped(TurnBack.Restart)
            if (arrival is Arrival.Stopped) {
                attempts.forget(key)
            }
            Verdict.Commit(arrival)
        }

    /**
     * Forgets [trip], which cannot go on for [reason].
     */
    internal suspend fun abandon(trip: Trip, reason: TurnBack): GoogleReturn = transactions.inTransaction {
        attempts.forget(trip.key)
        Verdict.Commit(GoogleReturn.TurnedBack(reason))
    }

    /**
     * Records that Google identified [person] on [trip]: in the attempt itself when they have an
     * account, or in a registration begun for them under [fresh] when they do not.
     */
    internal suspend fun land(trip: Trip, person: Identified, fresh: IssuedKey): GoogleReturn =
        transactions.inTransaction {
            val known = person.accountIn(accounts) != null
            Verdict.Commit(if (known) verified(trip, person) else registering(trip, person, fresh))
        }

    private fun verified(trip: Trip, person: Identified): GoogleReturn =
        when (attempts.save(trip.key, trip.attempt.withVerified(person.factor()))) {
            AttemptSaveOutcome.Saved -> GoogleReturn.Verified()
            // Only a request holding the same handshake could have changed it, and there is one.
            AttemptSaveOutcome.Superseded -> GoogleReturn.TurnedBack(TurnBack.Restart)
        }

    /**
     * The sign-in becomes a registration under a new secret (slice 3, fork 3): a registration is a
     * different purpose with a different policy, and one attempt never changes its purpose.
     */
    private fun registering(trip: Trip, person: Identified, fresh: IssuedKey): GoogleReturn {
        check(attempts.save(fresh.key, person.registration()) == AttemptSaveOutcome.Saved) {
            "A new registration found another attempt already kept under its key."
        }
        profiles.keep(fresh.key, person.profile)
        attempts.forget(trip.key)
        return GoogleReturn.Registering(fresh.secret)
    }

    override fun toString(): String = "GoogleTrips(attempts=$attempts)"
}
