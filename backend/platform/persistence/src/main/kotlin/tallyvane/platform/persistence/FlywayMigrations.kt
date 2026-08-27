package tallyvane.platform.persistence

import org.flywaydb.core.Flyway
import kotlin.time.Duration.Companion.seconds

/**
 * [Migrations] over Flyway.
 *
 * One location, `classpath:db/migration`, which Flyway walks — so a new capability
 * registers its migrations nowhere, which is the whole point of ADR-051's convention
 * over an explicit list. Versions are timestamps, so the order is global across
 * modules and a cross-schema foreign key cannot run before the table it references.
 *
 * ### Where Flyway's own table lives, and who creates schemas
 *
 * [SCHEMA] holds `flyway_schema_history`, and it is the *only* schema named here, so
 * Flyway creates that one and nothing else — every capability's schema is created by
 * that capability's own first migration. Listing them all here instead would be a
 * second place to forget a capability, which ADR-051 rejected by name.
 *
 * Flyway creates [SCHEMA] itself rather than being handed an `initSql` statement: that
 * setting is deprecated in favour of an `afterConnect` callback, which is a class to
 * write and register for one `create schema`, and letting Flyway create the one schema
 * it needs asks for neither.
 *
 * [SCHEMA] is `platform` rather than a name of its own because `MigrationSchemaSpec`
 * allows a migration under `db/migration/platform/` to name exactly that schema, and
 * the platform module owns it by the same convention every capability follows.
 */
public class FlywayMigrations(private val access: DatabaseAccess) : Migrations {
    override fun apply(): Migrations.Applied {
        val result = flyway().migrate()
        return Migrations.Applied(
            count = result.migrationsExecuted,
            // Flyway leaves `targetSchemaVersion` null when it applied nothing, so a
            // no-op run reported "none" and read, in a deploy log, as "there is no
            // schema" rather than "already up to date".
            version = result.targetSchemaVersion ?: result.initialSchemaVersion,
        )
    }

    override fun pending(): List<String> = flyway()
        .info()
        .pending()
        .map { migration -> migration.version.version }

    private fun flyway(): Flyway = Flyway
        .configure()
        .dataSource(access.url, access.user, access.password.revealed())
        .jdbcProperties(mapOf("options" to lockBound.asConnectionOption()))
        .locations(LOCATION)
        .schemas(SCHEMA)
        .defaultSchema(SCHEMA)
        .createSchemas(true)
        .load()

    private companion object {
        const val LOCATION = "classpath:db/migration"

        const val SCHEMA = "platform"

        /**
         * `lock_timeout`, and deliberately nothing else.
         *
         * Flyway opens its own connection, so the pool's bounds do not reach it. It needs this
         * one because `playground/ddl-locks/` measured what an `ALTER TABLE` does while it
         * queues: PostgreSQL's lock queue is ordered, so a plain `SELECT` arriving *after* the
         * DDL waits behind it and dies too. A migration that fails is rerunnable; an application
         * stalled behind that queue is an incident.
         *
         * No `statement_timeout` and no `idle_in_transaction_session_timeout`: a migration is
         * supposed to hold a long transaction, and cancelling one halfway is worse than waiting.
         *
         * Set here rather than as `initSql` — that setting is deprecated — and rather than in
         * each migration file, where it could be forgotten and nothing would say so.
         */
        val lockBound = SessionTimeouts(lock = 3.seconds)
    }
}
