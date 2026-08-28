package tallyvane.platform.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

private const val PLATFORM_BASELINE = "20260825020000"

private fun <T> on(access: DatabaseAccess, read: (Connection) -> T): T =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use(read)

private fun run(access: DatabaseAccess, vararg statements: String) {
    on(access) { connection ->
        connection.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }
}

private fun query(access: DatabaseAccess, sql: String): String? = on(access) { connection ->
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
    }
}

/**
 * Every case starts from a database of its own: virgin where the test is about migrating,
 * cloned from the migrated template where it is about what migrating produced.
 */
class FlywayMigrationsSpec :
    StringSpec(
        {
            "finds a migration nested under the one location it is given" {
                val applied = FlywayMigrations(PostgresFixture.empty()).apply()

                // The location is `classpath:db/migration`; the file is one directory
                // deeper, under `platform/`. ADR-051 leans on Flyway walking that, and
                // says so as a claim to be verified where it first runs. This is it.
                applied.count shouldBeGreaterThanOrEqualTo 1
                applied.version shouldBe PLATFORM_BASELINE
            }

            "applies nothing the second time, and still says which version is in place" {
                val migrations = FlywayMigrations(PostgresFixture.empty())
                migrations.apply()

                val second = migrations.apply()

                second.count shouldBe 0
                // Reporting `null` here read as "there is no schema" in a deploy log,
                // when it meant "already up to date".
                second.version shouldBe PLATFORM_BASELINE
            }

            "reports what is pending on a database nobody has migrated" {
                FlywayMigrations(PostgresFixture.empty()).pending() shouldBe listOf(PLATFORM_BASELINE)
            }

            "reports nothing pending once applied" {
                val migrations = FlywayMigrations(PostgresFixture.empty())
                migrations.apply()

                migrations.pending().shouldBeEmpty()
            }

            "keeps its own history table in the platform schema, out of public" {
                val access = PostgresFixture.empty()
                FlywayMigrations(access).apply()

                query(
                    access,
                    "select table_schema from information_schema.tables " +
                        "where table_name = 'flyway_schema_history'",
                ) shouldBe "platform"
            }

            "gives a column comparison that ignores case" {
                val access = PostgresFixture.empty()
                FlywayMigrations(access).apply()
                run(
                    access,
                    "create table people (email text collate platform.case_insensitive)",
                    "insert into people values ('Ivan@Mail.COM')",
                )

                query(access, "select count(*) from people where email = 'ivan@mail.com'") shouldBe "1"
            }

            "keeps that comparison working on a cloned database, where session state does not travel" {
                // The regression test for a real bug: the first version of this migration
                // installed citext and set search_path on the database. Both survived
                // `create database ... template ...` in appearance - the extension's
                // objects were copied - but the search_path setting was not, so the
                // clone compared case-sensitively with nothing to indicate it. A
                // collation is part of the column, so there is no session state to lose.
                val cloned = PostgresFixture.migrated()
                run(
                    cloned,
                    "create table people (email text collate platform.case_insensitive)",
                    "insert into people values ('Ivan@Mail.COM')",
                )

                query(cloned, "select count(*) from people where email = 'ivan@mail.com'") shouldBe "1"
            }

            "refuses two addresses that differ only by case" {
                val access = PostgresFixture.migrated()
                run(
                    access,
                    "create table people (email text collate platform.case_insensitive)",
                    "create unique index people_email on people (email)",
                    "insert into people values ('Ivan@Mail.COM')",
                )

                shouldThrow<SQLException> {
                    run(access, "insert into people values ('ivan@mail.com')")
                }
            }
        },
    )
