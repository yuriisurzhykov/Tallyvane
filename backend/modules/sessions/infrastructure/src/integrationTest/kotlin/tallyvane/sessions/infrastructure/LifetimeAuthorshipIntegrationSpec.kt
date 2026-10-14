package tallyvane.sessions.infrastructure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager
import java.sql.SQLException

private const val VERSION = "insert into sessions.lifetime_versions " +
    "(client_type, number, idle_millis, absolute_millis, freshness_millis, created_at%s) " +
    "values ('browser', 2, 3600000, 86400000, 300000, now()%s)"

private const val ACTIVATION = "insert into sessions.lifetime_activations " +
    "(client_type, number, activated_at%s) values ('browser', 1, now()%s)"

private const val TOO_LONG = 501 // One more than the 500 characters a comment may have.

/**
 * Who made a version, who put it in force and why are kept together or not at all (ADR-098), and the
 * administrators' lifetimes start as version 1, in force.
 *
 * Over a plain connection, as the conformance suite cannot show this: the adapter writes both or neither, so only
 * a statement that does not go through it can show that the database itself refuses the half.
 */
class LifetimeAuthorshipIntegrationSpec :
    StringSpec(
        {
            val someone = "'00000000-0000-4000-8000-0000000000ad'"

            "refuses a version with an author and no comment" {
                shouldThrow<SQLException> {
                    run(PostgresFixture.migrated(), VERSION.format(", made_by", ", $someone"))
                }.message shouldContain "lifetime_versions_author_and_comment_go_together"
            }

            "refuses a version with a comment and no author" {
                shouldThrow<SQLException> {
                    run(PostgresFixture.migrated(), VERSION.format(", comment", ", 'why'"))
                }.message shouldContain "lifetime_versions_author_and_comment_go_together"
            }

            "refuses an empty comment on a version" {
                shouldThrow<SQLException> {
                    run(PostgresFixture.migrated(), VERSION.format(", made_by, comment", ", $someone, ''"))
                }.message shouldContain "lifetime_versions_comment_length"
            }

            "refuses a comment on a version of more than 500 characters" {
                shouldThrow<SQLException> {
                    run(
                        PostgresFixture.migrated(),
                        VERSION.format(", made_by, comment", ", $someone, '${"x".repeat(TOO_LONG)}'"),
                    )
                }.message shouldContain "lifetime_versions_comment_length"
            }

            "refuses an activation with an author and no reason" {
                shouldThrow<SQLException> {
                    run(PostgresFixture.migrated(), ACTIVATION.format(", activated_by", ", $someone"))
                }.message shouldContain "lifetime_activations_author_and_reason_go_together"
            }

            "refuses an activation with an empty reason" {
                shouldThrow<SQLException> {
                    run(PostgresFixture.migrated(), ACTIVATION.format(", activated_by, reason", ", $someone, ''"))
                }.message shouldContain "lifetime_activations_reason_length"
            }

            "accepts an activation with an author and a reason, and one made by nobody" {
                val access = PostgresFixture.migrated()

                run(access, ACTIVATION.format(", activated_by, reason", ", $someone, 'Rolling back'"))
                run(access, ACTIVATION.format("", ""))
            }

            "starts the administrators' lifetimes as version 1, in force, made by nobody" {
                val access = PostgresFixture.migrated()

                single(
                    access,
                    "select idle_millis || '/' || absolute_millis || '/' || freshness_millis from " +
                        "sessions.lifetime_versions where client_type = 'admin' and number = 1 and made_by is null",
                ) shouldBe "3600000/28800000/300000"
                single(
                    access,
                    "select max(number)::text from sessions.lifetime_activations where client_type = 'admin'",
                ) shouldBe "1"
            }
        },
    ) {
    private companion object {
        fun run(access: DatabaseAccess, statement: String) {
            DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
                connection.createStatement().use { it.execute(statement) }
            }
        }

        fun single(access: DatabaseAccess, query: String): String =
            DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { rows ->
                        check(rows.next()) { "No row for: $query" }
                        rows.getString(1)
                    }
                }
            }
    }
}
