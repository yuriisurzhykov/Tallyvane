package tallyvane.platform.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthCheck

/**
 * Whether the schema this application expects is the schema the database has.
 *
 * ADR-051 applies migrations from a one-shot command the deploy runs, never at startup, and
 * this check is the other half of that decision: readiness *verifies* the work instead of
 * reporting on work it just did itself. A probe that migrates is a probe that always passes.
 *
 * ### What it sees after a module becomes a service
 *
 * Nothing has to change, and that falls out of ADR-051 rather than being arranged here.
 * Flyway is given one location, `classpath:db/migration`, and walks it — so it sees whatever
 * migrations are in the artefact it is running inside. In the monolith that is every
 * module's; in an extracted service it is that service's own. A registry of locations would
 * have needed editing at exactly the moment the split happened.
 *
 * ### Why its own dispatcher
 *
 * `Migrations.pending()` is blocking JDBC, and Flyway opens its own connection rather than
 * borrowing from the pool — so the pool-sized dispatcher `PostgresPersistence` owns is the
 * wrong place for it, and bare [Dispatchers.IO] is the right one: one short call per probe
 * cannot starve the sixty-four threads there, which is the concern that rules bare IO out
 * for pool work.
 */
public class MigrationsApplied(private val migrations: Migrations) : HealthCheck {
    override val name: String = NAME

    override val requiredForReadiness: Boolean = true

    override suspend fun check(): Health {
        val pending = withContext(Dispatchers.IO) { migrations.pending() }
        return if (pending.isEmpty()) Health.Up else Health.Down(Ailment.Behind(pending))
    }

    private companion object {
        const val NAME = "schema"
    }
}
