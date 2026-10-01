package tallyvane.authentication.infrastructure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager
import java.sql.SQLException

/**
 * The history of what the system demanded cannot be rewritten, by any statement at all.
 *
 * These go around the adapter on purpose, over a plain connection: the adapter never updates or
 * deletes, so a spec through it could not tell a refusal by the database from the adapter's own
 * restraint, and what is being protected against is exactly code, or a hand, that is not the adapter.
 */
class PolicyHistoryIntegrationSpec :
    StringSpec(
        {
            val statements = mapOf(
                "a version" to "update authentication.policy_versions set max_failures = 10",
                "a version, by deleting it" to "delete from authentication.policy_versions",
                "a step of a version" to "update authentication.policy_version_steps set necessity = 'always'",
                "a step of a version, by deleting it" to "delete from authentication.policy_version_steps",
                "the kinds of a step" to "update authentication.policy_version_step_kinds set kind = 'google'",
                "the kinds of a step, by deleting them" to "delete from authentication.policy_version_step_kinds",
                "an activation" to "update authentication.policy_activations set number = 1",
                "an activation, by deleting it" to "delete from authentication.policy_activations",
            )

            statements.forEach { (what, statement) ->
                "refuses to change $what" {
                    val access = PostgresFixture.migrated()

                    val refusal = shouldThrow<SQLException> { run(access, statement) }

                    refusal.message shouldContain "only ever added to"
                }
            }
        },
    ) {
    private companion object {
        fun run(access: DatabaseAccess, statement: String) {
            DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
                connection.createStatement().use { it.execute(statement) }
            }
        }
    }
}
