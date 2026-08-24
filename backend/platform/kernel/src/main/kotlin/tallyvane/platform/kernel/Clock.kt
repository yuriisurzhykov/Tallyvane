package tallyvane.platform.kernel

import kotlin.time.Instant

/**
 * Time as a collaborator, not as a static call.
 *
 * Domain and application code reads the current instant from this port so a
 * test can pin it. Reading wall-clock time from the standard library is an
 * architecture failure outside an implementation of this interface
 * (`no-ambient-time`). Tests construct a `ClockFake` in this module's
 * `src/test`; the fake is not nested on this type and is not shipped in the
 * production jar.
 *
 * Why a port rather than `java.time.Clock` or `kotlin.time.Clock.System`, and
 * why the fake is not nested: `backend/platform/kernel/README.md`.
 */
public interface Clock {
    /**
     * The current instant according to this clock.
     */
    public fun now(): Instant

    /**
     * The wall clock the process is actually running on.
     *
     * Named for the mechanism rather than `System`, which at a call site would
     * be indistinguishable from the `kotlin.time.Clock.System` this port exists
     * to keep out of domain code. It nests on the port because it reaches no
     * technology — no driver, no connection — so it drags nothing into a module
     * whose purpose is to stay free of both.
     */
    public class Wall : Clock {
        override fun now(): Instant = kotlin.time.Clock.System.now()
    }
}
