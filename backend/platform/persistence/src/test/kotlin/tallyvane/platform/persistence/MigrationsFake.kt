package tallyvane.platform.persistence

/**
 * [Migrations] whose answers are handed in, and which records whether it was asked to apply.
 *
 * The recording is the point rather than decoration: ADR-051 forbids a readiness check from
 * applying migrations, and "it did not apply them" is only assertable if something watched.
 *
 * Lives in `src/test` because only this module's tests need it (ADR-044).
 *
 * @param pending Versions [pending] reports; empty means the schema is current.
 */
internal class MigrationsFake(private val pending: List<String> = emptyList()) : Migrations {
    var applications: Int = 0
        private set

    override fun apply(): Migrations.Applied {
        applications++
        return Migrations.Applied(count = pending.size, version = pending.lastOrNull())
    }

    override fun pending(): List<String> = pending
}
