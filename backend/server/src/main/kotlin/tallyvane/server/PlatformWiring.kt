package tallyvane.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tallyvane.server.config.Configuration
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.persistence.FlywayMigrations
import tallyvane.platform.persistence.Migrations
import tallyvane.platform.persistence.Persistence
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The platform's own objects: one per capability, built once, closed once.
 *
 * Owned by whoever constructs it, so [close] is mandatory — it is what gives the connection pool and
 * the abandoned-work scope back. Construction touches no database (ADR-010).
 */
public class PlatformWiring(private val configuration: Configuration) : AutoCloseable {
    /**
     * The delegate rather than the value, so [close] can ask whether a pool was ever built instead
     * of building one in order to shut it down.
     */
    private val postgres = lazy {
        PostgresPersistence(
            access = configuration.database,
            size = configuration.pool,
        )
    }

    /**
     * Built on first use, not at construction.
     */
    public val persistence: Persistence get() = postgres.value

    /**
     * Eager: Flyway opens a connection per call and holds none between them, so there is nothing to
     * postpone.
     */
    public val migrations: Migrations = FlywayMigrations(configuration.database)

    public val ids: IdGenerator = IdGenerator.Uuid7()

    /**
     * Where work abandoned by `HealthCheck.Bounded` continues, and what [close] cancels.
     */
    public val abandoned: CoroutineScope = CoroutineScope(SupervisorJob())

    /**
     * Cancels abandoned work first and closes the pool second, so nothing is left waiting on a
     * connection that has gone.
     */
    override fun close() {
        abandoned.cancel()
        postgres.takeIf { it.isInitialized() }?.value?.close()
    }
}
