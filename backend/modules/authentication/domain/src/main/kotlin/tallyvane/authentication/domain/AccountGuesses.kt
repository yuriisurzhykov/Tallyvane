package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The wrong TOTP codes typed for one account lately, whichever attempt they came in (ADR-082).
 *
 * An attempt limits the guesses made in it, and a person who is sent back to Google gets a fresh one. So
 * the account keeps its own count: it is what turns five guesses per attempt into a rate no restart of
 * the attempt can beat. It slows guessing down and never locks the account, because a lock would let a
 * stranger lock the owner out by failing on purpose.
 */
public class AccountGuesses private constructor(private val wrongAt: List<Instant>) {
    /**
     * How long, at [now], a new guess has to wait: [firstDelay] after the first wrong code of the last
     * [WINDOW], doubled for each further one, and never more than [LONGEST_PAUSE]. Zero when there is
     * nothing to wait for.
     */
    public fun pauseLeft(now: Instant, firstDelay: Duration): Duration {
        val lately = wrongAt.filter { it > now - WINDOW }.sorted()
        val last = lately.lastOrNull() ?: return Duration.ZERO
        val doublings = (lately.size - 1).coerceAtMost(MAX_DOUBLINGS)
        val pause = minOf(firstDelay * (1L shl doublings).toInt(), LONGEST_PAUSE)
        return (last + pause - now).coerceAtLeast(Duration.ZERO)
    }

    override fun toString(): String = "AccountGuesses(${wrongAt.size})"

    public companion object {
        /**
         * How far back wrong codes count.
         */
        public val WINDOW: Duration = 15.minutes

        /**
         * The longest a person is ever made to wait between guesses.
         */
        public val LONGEST_PAUSE: Duration = 5.minutes

        private const val MAX_DOUBLINGS = 20

        /**
         * The guesses made at [times], in any order.
         */
        public fun at(times: List<Instant>): AccountGuesses = AccountGuesses(times.toList())
    }
}
