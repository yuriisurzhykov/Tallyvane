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

            val lateRows = mapOf(
                "a step to a version already kept" to
                    "insert into authentication.policy_version_steps (purpose, number, position, necessity) " +
                    "values ('admin_login', 1, 3, 'always')",
                "a kind to a step of a version already kept" to
                    "insert into authentication.policy_version_step_kinds (purpose, number, position, kind) " +
                    "values ('admin_login', 1, 1, 'totp')",
            )

            lateRows.forEach { (what, statement) ->
                "refuses to add $what, which would change what it demands" {
                    val access = PostgresFixture.migrated()

                    val refusal = shouldThrow<SQLException> { run(access, statement) }

                    refusal.message shouldContain "written together with the version"
                }
            }

            "accepts a version with its steps and kinds written in the one transaction that makes it" {
                val access = PostgresFixture.migrated()

                inOneTransaction(
                    access,
                    "insert into authentication.policy_versions " +
                        "(purpose, number, attempt_lifetime_millis, max_failures, first_delay_millis, created_at) " +
                        "values ('login', 2, 300000, 5, 1000, now())",
                    "insert into authentication.policy_version_steps (purpose, number, position, necessity) " +
                        "values ('login', 2, 1, 'always')",
                    "insert into authentication.policy_version_step_kinds (purpose, number, position, kind) " +
                        "values ('login', 2, 1, 'google')",
                )

                shouldThrow<SQLException> {
                    run(
                        access,
                        "insert into authentication.policy_version_steps (purpose, number, position, necessity) " +
                            "values ('login', 2, 2, 'always')",
                    )
                }.message shouldContain "written together with the version"
            }
        },
    ) {
    private companion object {
        fun run(access: DatabaseAccess, statement: String) {
            DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
                connection.createStatement().use { it.execute(statement) }
            }
        }

        fun inOneTransaction(access: DatabaseAccess, vararg statements: String) {
            DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
                connection.autoCommit = false
                connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
                connection.commit()
            }
        }
    }
}
