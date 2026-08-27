package tallyvane.app

import tallyvane.app.config.Configuration

/**
 * The process: it reads its settings, builds everything, listens, and gives it all back on the way
 * out.
 *
 * Skeleton: bodies are `TODO()` until `ApplicationSpec` is seen failing.
 *
 * ### Why the entry point is a companion and not a top-level function
 *
 * `no-top-level-functions` covers `app/`, and skipping it costs one of ten project-wide
 * `@ArchitectureException` slots, of which none are spent. The companion form costs nothing.
 * `@JvmStatic` is load-bearing rather than decorative: without it the static method lands on
 * `Application$Companion` and the JVM does not find `main`. Full reasoning in ADR-010.
 */
public class Application(private val configuration: Configuration) : AutoCloseable {
    /**
     * Starts listening, and returns as soon as it does.
     *
     * Non-blocking on purpose: every test needs to make a request after this, and `main` can wait
     * by itself. A `wait` flag was in the first sketch and removed — a parameter that only one of
     * two callers ever passes is a decision better made where it belongs.
     */
    public fun start(): Unit = TODO()

    override fun close(): Unit = TODO()

    public companion object {
        @JvmStatic
        public fun main(args: Array<String>): Unit = TODO()
    }
}
