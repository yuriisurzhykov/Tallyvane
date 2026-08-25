package tallyvane.platform.persistence

/**
 * Applying and inspecting the database's schema history.
 *
 * Two operations, because ADR-051 splits the two roles: a deploy-time command
 * *applies* migrations, and the readiness probe of §16.6 *verifies* that they are
 * applied rather than applying them itself. A probe that ran migrations would be
 * reporting on work it had just done.
 *
 * Blocking, and deliberately not `suspend`. Applying happens once from a one-shot
 * command, and [pending] is read by a `HealthCheck`, which already documents that a
 * check may block its thread and is bounded by a decorator. A `suspend` signature
 * over blocking work would promise what JDBC cannot give (ADR-058).
 */
public interface Migrations {
    /**
     * Applies whatever has not been applied yet.
     *
     * Idempotent by Flyway's own bookkeeping: running it twice applies nothing the
     * second time, which is what [Applied.count] of zero reports.
     */
    public fun apply(): Applied

    /**
     * Versions found on the classpath that the database has not recorded.
     *
     * Empty means the schema matches what this build carries. Non-empty is what makes
     * a readiness probe say no.
     */
    public fun pending(): List<String>

    /**
     * What one [apply] did.
     *
     * @property count migrations applied by this call; zero on a database already
     * up to date.
     * @property version the schema version afterwards, or `null` on a database that
     * has no migrations at all.
     */
    public data class Applied(val count: Int, val version: String?)
}
