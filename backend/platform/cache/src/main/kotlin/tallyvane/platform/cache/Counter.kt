package tallyvane.platform.cache

import tallyvane.platform.kernel.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How many times something happened to one `key` inside a moving window — the primitive a rate
 * limit is built from, not a rate limit itself.
 *
 * This port carries no policy of its own: not which key, not how long a window lasts, not what
 * threshold matters, and not what to do when the count crosses one. All four belong to whoever is
 * counting — five failed sign-ins in fifteen minutes is `identity`'s rule, not this module's, the
 * same way `platform:persistence` knows how to run a transaction but never what belongs inside
 * one.
 *
 * A key belongs to the module that reads it, the same rule §4.1 already applies to a Postgres
 * schema. Nothing here enforces that yet — unlike a schema, a `key` is a runtime string with no
 * compiled boundary to check it against — so a caller that reused another module's key would fail
 * silently rather than fail a build. Recorded as an open gap in this module's `README.md` rather
 * than guessed at with a mechanism nothing calls yet.
 */
public interface Counter {
    /**
     * Records one more occurrence of [key] and returns the count so far inside [window], measured
     * back from now.
     *
     * The first call for a key that has not been seen inside [window] starts its count at 1.
     * [window] is supplied on every call rather than fixed once per key, so this port never has to
     * remember what a caller meant by "the window" for a key it has not seen in a while — the
     * caller for whichever key it owns is the only place that has to stay consistent about it.
     */
    public suspend fun increment(key: String, window: Duration): Long

    /**
     * Counts in this process's memory and nowhere else.
     *
     * Lost on restart, and invisible to a second instance of the application if one is ever
     * running — both acceptable today because exactly one instance runs and a lost rate-limit
     * count only ever makes the next attempt look like the first one again, never the reverse.
     * `backend/.plans/backend-infra-cache-wiring.md` names the two conditions that would retire
     * this implementation for a shared one: surviving a restart, or more than one instance running
     * at once. Neither holds yet, so a network round trip and an external process buy nothing
     * today that this class does not already give for free.
     *
     * Reaches no technology beyond the standard library, only [clock] — the same reasoning
     * [tallyvane.platform.kernel.Clock.Wall] and [tallyvane.platform.kernel.IdGenerator.Uuid7]
     * already rest on for nesting a production implementation on its own port (ADR-047), which is
     * why this one nests too instead of living in a would-be `infrastructure` layer platform
     * modules do not have.
     */
    public class InMemory(private val clock: Clock) : Counter {
        private val windows = ConcurrentHashMap<String, Window>()

        override suspend fun increment(key: String, window: Duration): Long {
            val now = clock.now()
            return windows.compute(key) { _, current ->
                if (current == null || now - current.startedAt >= window) {
                    Window(startedAt = now, count = 1)
                } else {
                    current.copy(count = current.count + 1)
                }
            }!!.count
        }

        /**
         * One key's count since [startedAt]. A window that has run its full length is due for
         * replacement on the next [increment], not for an incremented count inside it — that is
         * what [Counter]'s own KDoc means by "the first call for a key that has not been seen
         * inside the window starts its count at 1".
         */
        private data class Window(val startedAt: Instant, val count: Long)
    }
}
