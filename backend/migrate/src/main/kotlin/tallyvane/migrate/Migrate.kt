package tallyvane.migrate

import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.FlywayMigrations

private const val URL = "TALLYVANE_DB_URL"
private const val USER = "TALLYVANE_DB_USER"
private const val PASSWORD = "TALLYVANE_DB_PASSWORD"

private fun required(name: String): String =
    System.getenv(name) ?: error("$name is not set. This command is run by the deploy, which supplies it.")

/**
 * Applies migrations, then exits.
 *
 * ADR-051's one-shot command: the deploy runs this before starting the application, and
 * the application never migrates at startup. Readiness therefore has something to
 * verify rather than reporting on work it just did itself.
 *
 * Fails loudly and with a non-zero status on anything unexpected, because a deploy that
 * continues past a failed migration starts an application against a schema it does not
 * match.
 */
fun main() {
    val access =
        DatabaseAccess(
            url = required(URL),
            user = required(USER),
            password = Secret(required(PASSWORD)),
        )
    val applied = FlywayMigrations(access).apply()
    println("Applied ${applied.count} migration(s). Schema version: ${applied.version ?: "none"}.")
}
