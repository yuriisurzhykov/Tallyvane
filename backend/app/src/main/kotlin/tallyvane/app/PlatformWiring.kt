package tallyvane.app

import kotlinx.coroutines.CoroutineScope
import tallyvane.app.config.Configuration
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.persistence.Migrations
import tallyvane.platform.persistence.Persistence

/**
 * The platform's own objects: one per capability, built once, closed once.
 *
 * Skeleton: bodies are `TODO()` until `PlatformWiringSpec` is seen failing.
 *
 * This is the "container" of ADR-010, written as a class with properties instead of a registry
 * keyed by type. `by lazy` is the deferred singleton, a plain `val` the eager one, and a function
 * would be one-per-call — three lifetimes without anything resolving at runtime.
 */
public class PlatformWiring(private val configuration: Configuration) : AutoCloseable {
    /**
     * Deferred on purpose. The pool must not be the reason the process fails to start: readiness
     * exists to say "up, but cannot serve", and it can only ever say it if the process is running.
     */
    public val persistence: Persistence get() = TODO()

    public val migrations: Migrations get() = TODO()

    public val ids: IdGenerator get() = TODO()

    /**
     * Where work abandoned by [tallyvane.platform.observability.health.HealthCheck.Bounded] goes.
     * It has to outlive the call that abandoned it and it has to be cancellable at shutdown, so it
     * belongs to whoever owns the process — this class.
     */
    public val abandoned: CoroutineScope get() = TODO()

    override fun close(): Unit = TODO()
}
